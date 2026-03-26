"""
Quantum Algorithm Sidecar
=========================
Implements the three-endpoint API contract from docs/use-predefined-algorithms.md.
Handles both one-shot (Grover) and variational (QAOA) algorithms.

Run:
    pip install -r requirements.txt
    python quantum_sidecar.py

Environment variables:
    CIRCUIT_GENERATOR_URL   Base URL of the quantum-circuit-generator service
                            (default: http://quantum-circuit-generator:5073)
    OBJECTIVE_EVAL_URL      Base URL of the objective-evaluation-service
                            (default: http://objective-evaluation-service:5072)

Endpoints:
    POST /generate-circuit   Builds a quantum circuit from classical problem parameters.
                             Grover: uses Qiskit locally.
                             QAOA:   delegates to quantum-circuit-generator.
    POST /process-results    Post-processes raw Qiskit Runtime Sampler results.
                             Grover: extracts the most frequent bitstring locally.
                             QAOA:   extracts counts, then delegates to objective-evaluation-service.
    POST /optimize           Runs one stateless SPSA step. The workflow passes all optimizer
                             state back on every call — no server-side session is maintained.
"""

import os
import numpy as np
import requests as http
from flask import Flask, request, jsonify

app = Flask(__name__)

CIRCUIT_GENERATOR_URL = os.environ.get(
    "CIRCUIT_GENERATOR_URL", "http://quantum-circuit-generator:5073"
)
OBJECTIVE_EVAL_URL = os.environ.get(
    "OBJECTIVE_EVAL_URL", "http://objective-evaluation-service:5072"
)


# ─── /generate-circuit ────────────────────────────────────────────────────────

@app.route("/generate-circuit", methods=["POST"])
def generate_circuit():
    data      = request.get_json(force=True)
    algorithm = data.get("algorithm", "grover")
    problem   = data.get("problem", {})
    params    = data.get("params")          # current variational params (QAOA only)

    if algorithm == "grover":
        circuit, shots = _generate_grover_circuit(problem)
        return jsonify({"circuit": circuit, "shots": shots})

    if algorithm == "qaoa":
        circuit, shots = _generate_qaoa_circuit(problem, params)
        return jsonify({"circuit": circuit, "shots": shots})

    return jsonify({"error": f"Unsupported algorithm: {algorithm}"}), 400


def _generate_grover_circuit(problem: dict) -> tuple[str, int]:
    """
    Build a Grover's search circuit using Qiskit and return it as OpenQASM 3.

    The circuit is transpiled to native basis gates — the IBM Quantum REST API does
    not transpile circuits server-side. The default gate set targets modern IBM backends
    (Eagle, Falcon R10) which use ECR as the two-qubit gate. Older backends that use CX
    instead can pass basis_gates explicitly, e.g. ["id", "rz", "sx", "x", "cx"].

    problem fields:
        target      – bitstring to search for, e.g. "11" (default "11")
        shots       – number of circuit repetitions (default 1024)
        basis_gates – list of native gate names for the target backend
                      (default ["id", "rz", "sx", "x", "ecr"])
    """
    from qiskit import QuantumCircuit
    from qiskit.circuit.library import PhaseOracle, GroverOperator
    from qiskit.compiler import transpile
    from qiskit.qasm3 import dumps

    target:          str  = problem.get("target", "11")
    shots:           int  = int(problem.get("shots", 1024))
    basis_gates_raw        = problem.get("basis_gates") or None
    if basis_gates_raw is None:
        basis_gates: list = ["id", "rx", "rz", "sx", "x", "cz"]
    elif isinstance(basis_gates_raw, str):
        basis_gates = [g.strip() for g in basis_gates_raw.split(",") if g.strip()]
    else:
        basis_gates = basis_gates_raw
    n = len(target)

    expr = " & ".join(
        f"x{i}" if bit == "1" else f"~x{i}"
        for i, bit in enumerate(reversed(target))
    )
    oracle     = PhaseOracle(expr)
    grover_op  = GroverOperator(oracle)
    qc         = QuantumCircuit(n, n)
    qc.h(range(n))
    qc.compose(grover_op, inplace=True)
    qc.measure(range(n), range(n))

    qc_native = transpile(qc, basis_gates=basis_gates, optimization_level=3)
    return dumps(qc_native), shots


def _generate_qaoa_circuit(problem: dict, params: list | None) -> tuple[str, int]:
    """
    Delegate circuit generation to the quantum-circuit-generator service.

    POST /algorithms/qaoa/maxcut
    The service accepts:
        adj_matrix      – 2-D list of floats (graph adjacency matrix)
        p               – QAOA depth (number of layers)
        circuit_format  – "openqasm2"
        parameters      – optional list [gamma_0, ..., gamma_{p-1}, beta_0, ..., beta_{p-1}]

    Returns OpenQASM 2.0 circuit string.
    """
    payload = {
        "adj_matrix":     problem["adj_matrix"],
        "p":              problem.get("p", 1),
        "circuit_format": "openqasm2",
    }
    if params:
        payload["parameters"] = params

    resp = http.post(
        f"{CIRCUIT_GENERATOR_URL}/algorithms/qaoa/maxcut",
        json=payload,
        timeout=30,
    )
    resp.raise_for_status()
    data = resp.json()
    return data["circuit"], int(problem.get("shots", 1024))


# ─── /process-results ─────────────────────────────────────────────────────────

@app.route("/process-results", methods=["POST"])
def process_results():
    data      = request.get_json(force=True)
    algorithm = data.get("algorithm", "grover")
    problem   = data.get("problem", {})
    results   = data.get("results", {})

    if algorithm == "grover":
        return jsonify(_process_grover_results(problem, results))

    if algorithm == "qaoa":
        return jsonify(_process_qaoa_results(problem, results))

    return jsonify({"error": f"Unsupported algorithm: {algorithm}"}), 400


def _process_grover_results(problem: dict, results: object) -> dict:
    """Extract the highest-frequency bitstring from Sampler output."""
    target: str = problem.get("target", "11")
    counts = _extract_counts(results)

    if not counts:
        return {"answer": None, "target": target, "found": False,
                "confidence": 0.0, "details": {}}

    best  = max(counts, key=counts.__getitem__)
    total = sum(counts.values())
    return {
        "answer":     best,
        "target":     target,
        "found":      best == target,
        "confidence": round(counts[best] / total, 4),
        "details":    {"counts": counts},
    }


def _process_qaoa_results(problem: dict, results: object) -> dict:
    """
    Extract bitstring counts from Sampler output, then delegate objective
    evaluation to the objective-evaluation-service.

    POST /objective/max-cut
    The service accepts:
        counts                  – {bitstring: frequency} dict
        adj_matrix              – graph adjacency matrix (must match the circuit)
        objFun                  – "expectation" | "cvar" | "gibbs"
        objFun_hyperparameters  – {"alpha": 0.2} for CVaR, {"eta": 10} for Gibbs
        visualization           – false (skip base64 PNG in response)

    Returns:
        objective_value  – scalar (negative cut weight for minimisation)
        costs            – per-bitstring cost breakdown
    """
    counts = _extract_counts(results)

    payload = {
        "counts":                counts,
        "adj_matrix":            problem["adj_matrix"],
        "objFun":                problem.get("objFun", "expectation"),
        "objFun_hyperparameters": problem.get("objFun_hyperparameters", {}),
        "visualization":         False,
    }

    resp = http.post(
        f"{OBJECTIVE_EVAL_URL}/objective/max-cut",
        json=payload,
        timeout=30,
    )
    resp.raise_for_status()
    data = resp.json()

    return {
        "objective_value": data["objective_value"],
        "costs":           data.get("costs", []),
        "counts":          counts,
    }


# ─── /optimize ────────────────────────────────────────────────────────────────

@app.route("/optimize", methods=["POST"])
def optimize():
    """
    Stateless SPSA optimiser step.

    The workflow passes back the full optimizer_state blob on every call so the
    sidecar never holds session state. The BPMN loop evaluates exactly the params
    returned as next_params and calls this endpoint again with the objective value.

    Request:
        algorithm          – "spsa" (only supported option for now)
        iteration          – current BPMN loop iteration count
        problem            – original problem context (passed through for reference)
        current_params     – flat list of floats: the params just evaluated
        objective_value    – scalar objective for current_params (from /process-results)
        optimizer_state    – opaque dict from the previous call (empty {} on first call)
        hyperparams        – optional SPSA tuning knobs (see _spsa_step for defaults)

    Response (not converged):
        converged          – false
        next_params        – flat list of floats to evaluate next
        iteration          – incremented counter
        optimizer_state    – updated state blob to pass back on the next call

    Response (converged):
        converged          – true
        optimal_params     – best params found
        objective_value    – best objective value achieved
        iteration          – final iteration count

    SPSA loop mechanics
    ───────────────────
    Three BPMN iterations per SPSA gradient step:

        iter k+0:  evaluate θ_k  (convergence check + calibration)
                   → sidecar returns θ_k + c_k·Δ_k  [phase: gradient_plus]
        iter k+1:  evaluate θ_k + c_k·Δ_k   (f_plus)
                   → sidecar returns θ_k − c_k·Δ_k  [phase: gradient_minus]
        iter k+2:  evaluate θ_k − c_k·Δ_k   (f_minus)
                   → sidecar computes ĝ = (f_plus−f_minus)/(2·c_k·Δ_k)
                      and returns θ_{k+1} = θ_k − a_k·ĝ  [phase: step]
    """
    data            = request.get_json(force=True)
    algorithm       = data.get("algorithm", "spsa")
    iteration       = int(data.get("iteration", 0))
    current_params  = data.get("current_params", [])
    objective_value = float(data.get("objective_value"))
    optimizer_state = data.get("optimizer_state") or {}
    hyperparams     = data.get("hyperparams") or {}

    if algorithm != "spsa":
        return jsonify({"error": f"Unsupported optimizer: {algorithm}"}), 400

    result = _spsa_step(iteration, current_params, objective_value,
                        optimizer_state, hyperparams)
    return jsonify(result)


def _spsa_step(
    iteration: int,
    current_params: list,
    objective_value: float,
    state: dict,
    hyperparams: dict,
) -> dict:
    """
    One step of the Simultaneous Perturbation Stochastic Approximation algorithm.

    Hyperparameters (all optional):
        a          gain sequence numerator for step size  (default 0.1)
        c          gain sequence numerator for perturbation magnitude (default 0.1)
        A          stability constant in step-size sequence (default 10)
        alpha      decay exponent for step size  (default 0.602, SPSA theory optimum)
        gamma      decay exponent for perturbation (default 0.101, SPSA theory optimum)
        tolerance  min improvement in objective to count as progress (default 1e-3)
        patience   consecutive non-improving gradient steps before declaring convergence
                   (default 5)
    """
    params = np.array(current_params, dtype=float)
    phase  = state.get("phase")            # None | "gradient_plus" | "gradient_minus"

    a         = float(hyperparams.get("a",         0.1))
    c         = float(hyperparams.get("c",         0.1))
    A         = float(hyperparams.get("A",         10.0))
    alpha     = float(hyperparams.get("alpha",     0.602))
    gamma_exp = float(hyperparams.get("gamma",     0.101))
    tol       = float(hyperparams.get("tolerance", 1e-3))
    patience  = int(hyperparams.get("patience",    5))

    # ── Convergence check + start new gradient step (phase None or "step") ────

    if phase is None or phase == "step":
        best_obj   = state.get("best_objective", float("inf"))
        no_improve = state.get("no_improve_count", 0)
        k          = state.get("spsa_k", 0)

        if objective_value < best_obj - tol:
            best_obj, no_improve = objective_value, 0
        else:
            no_improve += 1

        if no_improve >= patience and k > 0:
            return {
                "converged":      True,
                "optimal_params": params.tolist(),
                "objective_value": best_obj,
                "iteration":      iteration,
            }

        # Generate Rademacher perturbation vector
        ck    = c / (k + 1) ** gamma_exp
        delta = np.where(np.random.random(params.shape) > 0.5, 1.0, -1.0)

        return {
            "converged":       False,
            "next_params":     (params + ck * delta).tolist(),
            "iteration":       iteration + 1,
            "optimizer_state": {
                "phase":            "gradient_plus",
                "spsa_k":           k,
                "ck":               ck,
                "delta":            delta.tolist(),
                "theta_k":          params.tolist(),
                "best_objective":   best_obj,
                "no_improve_count": no_improve,
            },
        }

    # ── Got f(θ+cΔ), return θ−cΔ for evaluation ──────────────────────────────

    if phase == "gradient_plus":
        delta   = np.array(state["delta"])
        ck      = float(state["ck"])
        theta_k = np.array(state["theta_k"])

        return {
            "converged":       False,
            "next_params":     (theta_k - ck * delta).tolist(),
            "iteration":       iteration + 1,
            "optimizer_state": {**state, "phase": "gradient_minus",
                                "f_plus": objective_value},
        }

    # ── Got f(θ−cΔ), compute gradient, update θ ──────────────────────────────

    if phase == "gradient_minus":
        f_plus  = float(state["f_plus"])
        f_minus = objective_value
        delta   = np.array(state["delta"])
        ck      = float(state["ck"])
        theta_k = np.array(state["theta_k"])
        k       = int(state["spsa_k"])

        ak         = a / (A + k + 1) ** alpha
        grad       = (f_plus - f_minus) / (2.0 * ck * delta)
        theta_next = theta_k - ak * grad

        return {
            "converged":       False,
            "next_params":     theta_next.tolist(),
            "iteration":       iteration + 1,
            "optimizer_state": {
                "phase":            "step",
                "spsa_k":           k + 1,
                "best_objective":   state.get("best_objective", float("inf")),
                "no_improve_count": state.get("no_improve_count", 0),
            },
        }

    return {"error": f"Unknown SPSA phase: {phase}"}, 400


# ─── Shared utility ───────────────────────────────────────────────────────────

def _extract_counts(results: object) -> dict[str, int]:
    """
    Convert the raw Qiskit Runtime Sampler v2 PubResult payload to a
    {bitstring: count} dict suitable for both local Grover post-processing
    and the objective-evaluation-service.

    Expected shape of ibmqResult.result after JSON round-trip:
        {
          "results": [
            {
              "data": {
                "c": {
                  "array": [[0, 1], [1, 1], ...]   # one bit-vector per shot, LSB first
                }
              }
            }
          ]
        }

    Adapt this function if your connector serialises the payload differently.
    """
    counts: dict[str, int] = {}
    try:
        if isinstance(results, list):
            pub_results = results
        elif isinstance(results, dict) and "results" in results:
            pub_results = results["results"]
        else:
            pub_results = [results]
        for pub in pub_results:
            data = pub.get("data", {}) if isinstance(pub, dict) else {}
            for creg in data.values():
                for outcome in creg.get("array", []):
                    key = "".join(str(b) for b in reversed(outcome))
                    counts[key] = counts.get(key, 0) + 1
    except (AttributeError, TypeError, KeyError):
        pass
    return counts


# ─── Health check ─────────────────────────────────────────────────────────────

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


# ─── Entry point ──────────────────────────────────────────────────────────────

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
