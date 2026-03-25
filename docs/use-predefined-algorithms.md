# Using Predefined Quantum Algorithms via a Sidecar

Modelling a quantum circuit by hand in OpenQASM is impractical for most real-world algorithms. 
Instead, a **quantum circuit generation sidecar** — a lightweight Python/Qiskit service deployed alongside the connector — translates classical problem inputs into executable quantum circuits and interprets raw measurement results back into classical answers.

This document describes the architecture, the sidecar API contract, how classical post-processing fits in, and how to wire everything together in a BPMN workflow.

---

## Architecture Overview

```
Start Form (problem params)
    │
    ▼
[Generate Circuit]  ──── HTTP ────▶  Sidecar: POST /generate-circuit
    │                               Returns: circuit / params
    ▼
[IBM Quantum Connector]             Submits job, polls for result
    │                               Returns: ibmqResult (raw quantum output)
    ▼
[Process Results]   ──── HTTP ────▶  Sidecar: POST /process-results
    │                               Returns: classicalResult
    ▼
User Task / End
```

The IBM Quantum Connector is unchanged. The sidecar owns all algorithm logic — the BPMN workflow only orchestrates data flow between the three steps.

The sidecar is a separate Docker container deployed alongside the connector, reachable at `http://localhost:<port>` or by service name in Kubernetes/Compose.

---

## Sidecar API Contract

### `POST /generate-circuit`

Translates classical problem parameters into a quantum circuit or Qiskit Runtime job parameters.

**Request:**
```json
{
  "algorithm": "grover",
  "problem": { }
}
```

**Response (OpenQASM mode):**
```json
{
  "circuit": "OPENQASM 3.0; ...",
  "shots": 1024
}
```

**Response (Direct Params mode):**
```json
{
  "params": { "pubs": [ ["<circuit>", null, 1024] ] }
}
```

The response maps directly to the IBM Quantum Connector's `circuit` / `shots` or `params` input fields via the result expression of the HTTP connector task.

### `POST /process-results`

Applies classical post-processing to the raw quantum measurement data and returns a human-readable or machine-readable answer.

**Request:**
```json
{
  "algorithm": "grover",
  "problem": { },
  "results": { }
}
```

The `results` field contains `ibmqResult.result` as returned by the connector — the raw Qiskit Runtime result payload.

**Response:**
```json
{
  "answer": "...",
  "confidence": 0.95,
  "details": { }
}
```

---

## BPMN Workflow Structure

### One-shot algorithms (Grover's, Bernstein-Vazirani, etc.)

Algorithms that require a single circuit execution use the standard polling workflow with two additional HTTP service tasks:

```
Start → Generate Circuit → Submit Job → [Poll Loop] → Process Results → Review → End
```

**Process variables:**

| Variable | Set by | Used by |
|---|---|---|
| `problem` | Start form | Generate Circuit, Process Results |
| `circuit` / `params` | Generate Circuit | IBM Quantum Connector |
| `ibmqJobId` | IBM Quantum Connector (submit) | IBM Quantum Connector (poll) |
| `ibmqResult` | IBM Quantum Connector (poll) | Process Results |
| `classicalResult` | Process Results | Review user task |

### Variational algorithms (VQE, QAOA)

Variational algorithms iterate between a quantum circuit execution and a classical optimizer that adjusts the circuit parameters until convergence. This requires a third sidecar endpoint and an additional loop in the BPMN:

```
Start
  │
  ▼
Generate Circuit (initial params)
  │
  ▼
Submit Job → [Poll Loop] ──────────────────────────────────┐
  │                                                         │
  ▼                                                         │
Optimize (classical)                                        │
  │                                                         │
  ├── Not converged ──▶ Generate Circuit (updated params) ──┘
  │
  └── Converged ──▶ Process Final Result → Review → End
```

#### `POST /optimize`

Called after each quantum execution. Runs the classical optimizer step and returns either updated circuit parameters for the next iteration or a convergence signal.

**Request:**
```json
{
  "algorithm": "vqe",
  "iteration": 3,
  "problem": { },
  "current_params": { },
  "results": { }
}
```

**Response (not converged):**
```json
{
  "converged": false,
  "next_params": { },
  "iteration": 4
}
```

**Response (converged):**
```json
{
  "converged": true,
  "optimal_params": { },
  "energy": -1.137
}
```

The BPMN gateway after the optimize task routes on `converged`:
- `= converged = false` → loop back to Generate Circuit, passing `next_params` as the new circuit parameters
- `= converged = true` → proceed to Process Final Result

The sidecar manages optimizer state statelessly — the workflow passes the full current state (parameters, iteration count, previous results) on every call, so no server-side session is needed.

---

## Post-Processing by Algorithm Type

| Algorithm | Raw output | Post-processing |
|---|---|---|
| Grover's search | Bitstring counts | Extract highest-frequency bitstring |
| Bernstein-Vazirani | Bitstring counts | Read hidden string directly |
| QAOA | Bitstring counts | Map bitstring to combinatorial solution (e.g. graph cut) |
| VQE | Expectation values | Return minimum energy and corresponding parameters |
| Sampler (generic) | Bitstring count distribution | Algorithm-specific interpretation |
| Estimator (generic) | Observable expectation values | Algorithm-specific interpretation |

All post-processing is encapsulated in the sidecar's `/process-results` endpoint. The BPMN workflow only receives the final `classicalResult`.

---

## Deployment

The sidecar runs as a Docker container alongside the IBM Quantum Connector. A minimal `docker-compose.yml` would look like:

```yaml
services:
  ibmq-connector:
    image: ibmq-connector:latest
    ...

  quantum-sidecar:
    image: quantum-sidecar:latest
    ports:
      - "5000:5000"
```

The connector reaches the sidecar at `http://quantum-sidecar:5000` via the Camunda HTTP Connector service tasks. The sidecar URL should be stored as a Camunda secret or process variable to keep it configurable across environments.

---

## Camunda Marketplace Publication Strategy

### Why the sidecar operations are not part of the IBM Quantum Connector

An alternative design would be to integrate the sidecar calls directly as additional operations (`GENERATE_CIRCUIT`, `PROCESS_RESULTS`, `OPTIMIZE`) inside the IBM Quantum Connector. This was deliberately rejected for the following reasons:

- **Separation of concerns** — the connector's responsibility is IBM Quantum API interaction. Sidecar calls are algorithm-specific and optional; bundling them would couple an infrastructure component to a domain-specific concern.
- **Replaceability** — keeping the sidecar as an independent HTTP service means it can be swapped out, versioned separately, or reused from other workflows without touching the connector.
- **Marketplace discoverability** — the connector listing stays clean and installable without any Python infrastructure dependency, which is critical for a low-friction marketplace experience.

### Two-listing publication strategy

The sidecar integration is published as two separate but related Camunda marketplace listings:

**1. IBM Quantum Connector** *(existing)*
The connector JAR and its element template. No sidecar dependency. Installable standalone and usable immediately with manually authored circuits.

**2. IBM Quantum Algorithm Accelerator** *(planned)*
A marketplace accelerator listing that bundles:
- Element templates for the sidecar HTTP service tasks (`/generate-circuit`, `/process-results`, `/optimize`), giving modelers the same pre-configured, discoverable experience as a native connector
- Pre-wired BPMN templates for one-shot and variational algorithm workflows
- Documentation on deploying the sidecar container

The accelerator listing explicitly declares the IBM Quantum Connector as a prerequisite, so users understand the dependency relationship before installing.

This split maps naturally to what the Camunda marketplace already supports: connector listings for runtime components and accelerator/template listings for workflow patterns and tooling.
