"""
Quantum Algorithm Sidecar
=========================
Implements the three-endpoint API contract from docs/use-predefined-algorithms.md.
Handles both one-shot (Grover) and variational (QAOA) algorithms.

Run:
    pip install -r requirements.txt
    python quantum_sidecar.py

Environment variables:
    OBJECTIVE_EVAL_URL      Base URL of the objective-evaluation-service
                            (default: http://objective-evaluation-service:5072)

Endpoints:
    POST /generate-circuit   Builds a quantum circuit from classical problem parameters.
                             Grover: uses Qiskit locally.
                             QAOA:   uses Qiskit locally.
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
from qiskit import QuantumCircuit
from qiskit.compiler import transpile
from qiskit.qasm3 import dumps as qasm3_dumps
from qiskit_ibm_runtime import QiskitRuntimeService

app = Flask(__name__)

OBJECTIVE_EVAL_URL = os.environ.get(
    "OBJECTIVE_EVAL_URL", "http://objective-evaluation-service:5072"
)


# ─── Shared circuit helpers ───────────────────────────────────────────────────

_DEFAULT_BASIS_GATES = ["id", "rz", "sx", "x", "ecr"]

_backend_cache: dict = {}


def _get_ibm_backend(api_key: str, ibmq_instance: str, backend_name: str):
    """Fetch and cache the IBM Quantum backend object via Qiskit IBM Runtime."""
    cache_key = (ibmq_instance, backend_name)
    if cache_key not in _backend_cache:
        service = QiskitRuntimeService(
            channel="ibm_cloud",
            token=api_key,
            instance=ibmq_instance,
        )
        _backend_cache[cache_key] = service.backend(backend_name)
    return _backend_cache[cache_key]


def _resolve_transpile_args(problem: dict, backend_info: dict) -> dict:
    """Return kwargs for Qiskit transpile().

    Priority:
    1. problem.basis_gates – explicit user override (comma-separated string or list)
    2. IBM Quantum backend object – auto-fetched via backend_info credentials;
       lets Qiskit handle basis_gates, coupling_map, and all other constraints
    3. _DEFAULT_BASIS_GATES fallback
    """
    raw = problem.get("basis_gates") or None
    if raw is not None:
        gates = [g.strip() for g in raw.split(",") if g.strip()] if isinstance(raw, str) else raw
        return {"basis_gates": gates}

    api_key  = backend_info.get("api_key")
    instance = backend_info.get("ibmq_instance")
    backend  = backend_info.get("backend")
    if api_key and instance and backend:
        try:
            return {"backend": _get_ibm_backend(api_key, instance, backend)}
        except Exception as exc:
            app.logger.warning("Could not fetch IBM backend %s: %s", backend, exc)

    return {"basis_gates": _DEFAULT_BASIS_GATES}


# ─── /generate-circuit ────────────────────────────────────────────────────────

@app.route("/generate-circuit", methods=["POST"])
def generate_circuit():
    data      = request.get_json(force=True)
    algorithm = data.get("algorithm", "grover")
    problem   = data.get("problem", {})
    params    = data.get("params")          # current variational params (QAOA only)
    backend_info = {
        "api_key":       data.get("apiKey"),
        "ibmq_instance": data.get("ibmqInstance"),
        "backend":       data.get("backend"),
    }

    if algorithm == "grover":
        circuit, shots = _generate_grover_circuit(problem, backend_info)
        return jsonify({"circuit": circuit, "shots": shots})

    if algorithm == "qaoa":
        circuit, shots, used_params = _generate_qaoa_circuit(problem, params, backend_info)
        return jsonify({"circuit": circuit, "shots": shots, "params": used_params})

    return jsonify({"error": f"Unsupported algorithm: {algorithm}"}), 400


def _generate_grover_circuit(problem: dict, backend_info: dict) -> tuple[str, int]:
    """
    Build a Grover's search circuit using Qiskit and return it as OpenQASM 3.

    The circuit is transpiled to native basis gates — the IBM Quantum REST API does
    not transpile circuits server-side. The default gate set targets modern IBM backends
    (Eagle, Falcon R10) which use ECR as the two-qubit gate. Older backends that use CX
    instead can pass basis_gates explicitly, e.g. ["id", "rz", "sx", "x", "cx"].

    problem fields:
        target      – bitstring to search for, e.g. "11" (default "11")
        shots       – number of circuit repetitions (default 1024)
        basis_gates – see _resolve_basis_gates()
    """
    from qiskit.circuit.library import PhaseOracle, GroverOperator

    target:         str  = problem.get("target", "11")
    shots:          int  = int(problem.get("shots", 1024))
    transpile_args: dict = _resolve_transpile_args(problem, backend_info)
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

    qc_native = transpile(qc, **transpile_args, optimization_level=3)
    return qasm3_dumps(qc_native), shots


def _generate_qaoa_circuit(problem: dict, params: list | None, backend_info: dict) -> tuple[str, int, list]:
    """
    Build a QAOA MaxCut circuit using Qiskit and return it as OpenQASM 3.

    The circuit is transpiled to native basis gates — the IBM Quantum REST API does
    not transpile circuits server-side.

    problem fields:
        adj_matrix  – 2-D list of floats representing the graph
        p           – QAOA depth / number of layers (default 1)
        shots       – number of circuit repetitions (default 1024)
        basis_gates – see _resolve_basis_gates()
    """
    import json as _json

    adj_matrix = problem["adj_matrix"]
    if isinstance(adj_matrix, str):
        adj_matrix = _json.loads(adj_matrix)

    p     = int(problem.get("p", 1))
    shots = int(problem.get("shots", 1024))

    if not params:
        params = [0.5] * (2 * p)

    n     = len(adj_matrix)
    edges = [
        (i, j, float(adj_matrix[i][j]))
        for i in range(n)
        for j in range(i + 1, n)
        if adj_matrix[i][j] != 0
    ]

    qc = QuantumCircuit(n, n)
    qc.h(range(n))

    for layer in range(p):
        gamma = float(params[layer])
        beta  = float(params[p + layer])

        # Cost layer: RZZ(2γw) for each weighted edge
        for i, j, w in edges:
            qc.rzz(2.0 * gamma * w, i, j)

        # Mixer layer: RX(2β) on each qubit
        for i in range(n):
            qc.rx(2.0 * beta, i)

    qc.measure(range(n), range(n))

    qc_native = transpile(qc, **_resolve_transpile_args(problem, backend_info), optimization_level=3)
    return qasm3_dumps(qc_native), shots, params


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
    import json as _json

    adj_matrix = problem["adj_matrix"]
    if isinstance(adj_matrix, str):
        adj_matrix = _json.loads(adj_matrix)

    counts = _extract_counts(results)

    payload = {
        "counts":                counts,
        "adj_matrix":            adj_matrix,
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
        tolerance      min improvement in objective to count as progress (default 1e-3)
        patience       consecutive non-improving gradient steps before declaring convergence
                       (default 5)
        max_iterations hard cap on total BPMN loop iterations before forcing convergence
                       (default 100)
    """
    params = np.array(current_params, dtype=float)
    phase  = state.get("phase")            # None | "gradient_plus" | "gradient_minus"

    a             = float(hyperparams.get("a",             0.1))
    c             = float(hyperparams.get("c",             0.1))
    A             = float(hyperparams.get("A",             10.0))
    alpha         = float(hyperparams.get("alpha",         0.602))
    gamma_exp     = float(hyperparams.get("gamma",         0.101))
    tol           = float(hyperparams.get("tolerance",     1e-3))
    patience      = int(hyperparams.get("patience",        5))
    max_iterations = int(hyperparams.get("max_iterations", 100))

    # ── Convergence check + start new gradient step (phase None or "step") ────

    if phase is None or phase == "step":
        best_obj   = state.get("best_objective", float("inf"))
        no_improve = state.get("no_improve_count", 0)
        k          = state.get("spsa_k", 0)

        if objective_value < best_obj - tol:
            best_obj, no_improve = objective_value, 0
        else:
            no_improve += 1

        if (no_improve >= patience and k > 0) or iteration >= max_iterations:
            return {
                "converged":          True,
                "convergence_reason": "max_iterations" if iteration >= max_iterations else "converged",
                "optimal_params":     params.tolist(),
                "objective_value":    best_obj,
                "iteration":          iteration,
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
                  "samples": ["0x3", "0x1", ...],  # one hex integer per shot
                  "num_bits": 2
                }
              }
            }
          ]
        }

    The "samples" format is used by the current IBM Quantum REST API (Sampler v2) for all
    backends. The legacy "array" format (bit-vectors, LSB first) is retained for backwards
    compatibility with older API versions.

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
                if "samples" in creg:
                    num_bits = int(creg.get("num_bits", 1))
                    for sample in creg["samples"]:
                        key = format(int(sample, 16), f"0{num_bits}b")
                        counts[key] = counts.get(key, 0) + 1
                else:
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
