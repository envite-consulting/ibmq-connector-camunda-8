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

![Grover's Search workflow](images/grover-workflow.png)

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

> **Warning (Camunda SaaS only):** The sidecar is called by the built-in Camunda HTTP Connector (`io.camunda:http-json:1`), which executes inside the **Camunda SaaS** infrastructure — not locally.
> This means `http://quantum-sidecar:5000` is not reachable from the cloud.
> The sidecar must be publicly accessible when using Camunda SaaS.
>
> When running a **local Camunda setup** (e.g. via Docker), the HTTP Connector executes locally and can reach the sidecar by its Docker service name without any extra steps.
>
> For **Camunda SaaS**, use a tunneling tool such as [ngrok](https://ngrok.com/) to expose the sidecar during testing:
> ```bash
> ngrok http 5000
> ```
> Then use the generated public URL (e.g. `https://xxxx.ngrok.io`) as the `sidecarUrl` below.
> For production deployments, host the sidecar container on a publicly reachable endpoint.

1. Start the connector and sidecar:
   ```bash
   cd example/predefined-algorithms
   docker compose up --build
   ```

2. Deploy the workflow and forms to your Camunda cluster by uploading the files from `example/predefined-algorithms/grover/` via the Camunda Web Modeler.

3. Start a process instance via Camunda Tasklist with the following start form inputs:

   | Field | Example value |
   |---|---|
   | Target bitstring | `11` |
   | Shots | `1024` |
   | Sidecar URL | your public sidecar URL, e.g. `https://xxxx.ngrok.io` |
   | IBM Quantum API Key | `{{secrets.IBMQ_API_KEY}}` |
   | IBM Quantum URL | `https://quantum.cloud.ibm.com/api` |
   | IBM Quantum Instance | `{{secrets.IBMQ_INSTANCE}}` |
   | Backend | your backend name, e.g. `ibm_brisbane` |

4. After the quantum job completes, a Review user task appears in Tasklist. `classicalResult.answer` should equal the target bitstring, with `classicalResult.found = true` and a high `classicalResult.confidence`.

---

---

## QAOA / Max-Cut

TODO
