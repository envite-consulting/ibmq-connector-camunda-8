# Configuration Properties for the Connector

The connector provides the following configuration properties to enable executing quantum circuits or complete quantum programs using IBMQ:

### IBMQ

| Field | Description | Default |
|---|---|---|
| **IBMQ API Key** | IBM Cloud API key. Use a Camunda secret: `{{secrets.IBMQ_API_KEY}}` | — |
| **IBMQ URL** | IBMQ endpoint | `https://us-east.quantum-computing.ibm.com` |
| **IBMQ Instance** | Instance in `hub/group/project` format, e.g. `ibm-q/open/main` | — |
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