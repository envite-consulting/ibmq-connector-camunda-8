# Example Use Cases & HowTos

The following examples demonstrate how to use the IBM Quantum Connector and the [IBM Quantum Algorithm Accelerator pattern](use-predefined-algorithms.md) in real workflows.
Each example includes a ready-to-run BPMN workflow, element templates, and setup instructions.

| Example | Algorithm type | Reference                                               |
|---|---|---------------------------------------------------------|
| [Grover's Search](#grovers-search) | One-shot | [Grover, 1996](https://arxiv.org/abs/quant-ph/9605043)  |
| [QAOA / Max-Cut](#qaoa--max-cut) | Variational | [Farhi et al., 2014](https://arxiv.org/abs/1411.4028)   |

---

## Grover's Search

**Example workflow:** [`example/predefined-algorithms/grover/grover-search-workflow.bpmn`](../example/predefined-algorithms/grover/grover-search-workflow.bpmn)

Grover's search algorithm finds a marked element in an unstructured search space of size N with O(√N) quantum circuit evaluations, compared to O(N) for a classical linear scan.
This example demonstrates the [IBM Quantum Algorithm Accelerator pattern](use-predefined-algorithms.md): a lightweight Python sidecar translates a classical problem description into an executable quantum circuit and interprets the raw measurement results back into a classical answer — the IBM Quantum Connector itself is unchanged.

### Problem

Given a target bitstring (e.g. `"11"`), find it in the search space of all 2-qubit bitstrings using a single quantum circuit execution.

### Workflow structure

```
Start Form → Generate Circuit → Submit Job → [Poll Loop] → Process Results → Review → End
```

| Step | Component | What it does |
|---|---|---|
| Start Form | Camunda Form | Collects `problem` (target bitstring, shots), `sidecarUrl`, `apiKey`, `ibmqUrl`, `ibmqInstance`, `backend` |
| Generate Circuit | HTTP Connector → Sidecar `/generate-circuit` | Builds and transpiles a Grover circuit to native IBM Quantum basis gates; returns `circuit` (OpenQASM 3) and `shots` |
| Submit Job | IBM Quantum Connector (`SUBMIT_JOB`) | Submits the circuit to the selected IBM Quantum backend; returns `ibmqJobId` |
| Poll Loop | Timer + IBM Quantum Connector (`GET_JOB_RESULT`) | Waits 30 s, checks job status, loops until terminal state |
| Process Results | HTTP Connector → Sidecar `/process-results` | Extracts the highest-frequency bitstring from the Sampler output; returns `classicalResult` with `answer`, `found`, `confidence`, and `counts` |
| Review | User Task | Presents the search result to a human |

### Process variables

| Variable | Set by | Used by |
|---|---|---|
| `problem` | Start form | Generate Circuit, Process Results |
| `sidecarUrl` | Start form | Generate Circuit, Process Results |
| `apiKey` | Start form | Submit Job, Check Job |
| `ibmqUrl` | Start form | Submit Job, Check Job |
| `ibmqInstance` | Start form | Submit Job, Check Job |
| `backend` | Start form | Submit Job |
| `circuit` | Generate Circuit | Submit Job |
| `shots` | Generate Circuit | Submit Job |
| `ibmqJobId` | Submit Job | Check Job |
| `ibmqStatus` | Check Job | Poll gateway |
| `ibmqResult` | Check Job | Process Results |
| `classicalResult` | Process Results | Review user task |

### Running the example

1. Start the connector and sidecar:
   ```bash
   cd example/predefined-algorithms
   docker compose up --build
   ```

2. Deploy the workflow and form to your Camunda cluster (done automatically if `ibmq.example.deploy=true` is set in `application.properties`).

3. Start a process instance via Camunda Tasklist with the following start form inputs:

   | Field | Example value |
   |---|---|
   | Target bitstring | `11` |
   | Shots | `1024` |
   | Sidecar URL | `http://quantum-sidecar:5000` |
   | IBM Quantum API Key | your API key |
   | IBM Quantum URL | `https://quantum.cloud.ibm.com/api` |
   | IBM Quantum Instance | your instance CRN |
   | Backend | `ibmq_qasm_simulator` |

4. After the quantum job completes, a Review user task appears in Tasklist. `classicalResult.answer` should equal the target bitstring, with `classicalResult.found = true` and a high `classicalResult.confidence`.

---

---

## QAOA / Max-Cut

TODO
