# Connector Configuration and Output Reference

The connector provides the following configuration properties to enable executing quantum circuits or complete quantum programs using IBMQ:

### IBMQ

| Field | Description | Default |
|---|---|---|
| **IBMQ API Key** | IBM Cloud API key. Use a Camunda secret: `{{secrets.IBMQ_API_KEY}}` | — |
| **IBMQ URL** | IBMQ endpoint | `https://quantum.cloud.ibm.com/api` |
| **IBMQ Instance CRN** | IBM Quantum instance Cloud Resource Name (CRN). Find it in IBM Cloud → Resource list → your Quantum Computing instance → Details. | — |
| **IBMQ Backend** | Target quantum computer | `ibmq_qasm_simulator` |

### Quantum Circuit

| Field | Description | Default             | Condition |
|---|---|---------------------|---|
| **Program ID** | Qiskit Runtime program to execute (`sampler`, `estimator`). Use Sampler to directly execute OpenQASM circuits. | `sampler`           | — |
| **Circuit Input Mode** | How the circuit is supplied: `OpenQASM (2 or 3)` or `Direct Params (JSON)` | `OpenQASM (2 or 3)` | Program ID = Sampler |
| **Quantum Circuit (OpenQASM)** | OpenQASM 2 or 3 circuit string. Automatically wrapped in a Sampler V2 PUB. | —                   | Program ID = Sampler, Circuit Input Mode = OpenQASM |
| **OpenQASM Version** | Version of the supplied circuit (`OpenQASM 3 (recommended)`, `OpenQASM 2`) | `OpenQASM 3`                | Program ID = Sampler, Circuit Input Mode = OpenQASM |
| **Shots** | Number of circuit repetitions | `1024`              | Program ID = Sampler, Circuit Input Mode = OpenQASM |
| **Params (JSON)** | Full Qiskit Runtime job params as a JSON string, e.g. `{"pubs": [["<circuit>", null, 1024]]}` | —                   | Program ID = Estimator, or Sampler + Direct Params |

### Execution

| Field | Description | Default |
|---|---|---|
| **Wait for Result** | Poll until the job reaches a terminal state before completing the task | `true` |
| **Timeout (seconds)** | Maximum time to wait for a result | `300` |
| **Poll Interval (seconds)** | How often to check the job status | `5` |

---

## Connector Output

The connector returns a `response` object after the service task completes. Use the **Result Expression** in the element template (or task headers) to map fields into process variables.

| Field | Type | Description |
|---|---|---|
| `response.jobId` | String | IBM Quantum job identifier |
| `response.status` | String | Job status: `QUEUED`, `COMPLETED`, `FAILED`, `CANCELLED`, or `ERROR` |
| `response.result` | FEEL context | Raw result payload from the IBM Quantum API. Only populated when the job completed successfully. Can be navigated in FEEL, e.g. `response.result.results[1].data`. `null` otherwise. |
| `response.resultUrl` | String | URL to the job details page in the IBM Quantum web UI |

### Behavior by operation

**`SUBMIT_JOB` with `waitForResult=true`** — the task blocks until the job reaches a terminal state. All four fields are populated; `result` is non-null only for `COMPLETED` jobs.

**`SUBMIT_JOB` with `waitForResult=false`** — the task completes immediately after submission. `status` is always `QUEUED` and `result` is always `null`. Use the `GET_JOB_RESULT` operation in a subsequent polling loop to retrieve the final result.

**`GET_JOB_RESULT`** — performs a single status check. `result` is populated only once `status` is `COMPLETED`.

### Example result expressions

Map all output fields into a single process variable:
```
= {jobId: response.jobId, status: response.status, result: response.result, resultUrl: response.resultUrl}
```

Extract only the job ID after a non-blocking submission (polling pattern):
```
= {ibmqJobId: response.jobId}
```

Access a specific field from the result payload in a downstream FEEL expression:
```
= ibmqResult.result.results[1].data
```