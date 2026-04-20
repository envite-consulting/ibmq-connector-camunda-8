# Getting Started

This guide walks you through the IBM Quantum and Camunda prerequisites and your first end-to-end circuit execution.
For connector installation steps see the [README](../README.md#-how-to-run).

---

## 1. IBM Quantum Prerequisites

### Create an IBM Quantum account

Sign up at [quantum.cloud.ibm.com](https://quantum.cloud.ibm.com/).
An IBM Cloud account is required.

### Obtain an API key

1. Open [IBM Cloud → Manage → Access (IAM) → API keys](https://cloud.ibm.com/iam/apikeys).
2. Click **Create** and give the key a name (e.g. `camunda-connector`).
3. Copy the key immediately — it is only shown once.

### Find your Instance CRN

The Connector requires the **Cloud Resource Name (CRN)** of your IBM Quantum Computing instance:

1. Go to [IBM Quantum Platform](https://quantum.cloud.ibm.com/).
2. Under **Instances** select your quantum computing instance.
3. Copy the **CRN** from the instance details panel.

It looks like: `crn:v1:bluemix:public:quantum-computing:<region>:a/<account-id>/<instance-id>::`

### Choose a backend

List the backends available to your instance via the IBM Quantum Platform UI or via CLI:

First, obtain a bearer token as follows:

```bash
curl -X POST 'https://iam.cloud.ibm.com/identity/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \ 
  -d 'grant_type=urn:ibm:params:oauth:grant-type:apikey&apikey=<your-iam-token>'
```

> **Note:** The generated bearer token expires after 1 hour.


```bash
curl -s -X GET "https://quantum.cloud.ibm.com/api/v1/backends" \
  -H "Authorization: Bearer <your-bearer-token>" \
  -H "Service-CRN: <your-crn>" \
  | jq '[.devices]'
```

For testing, `ibm_kingston` or another real backend is required — the online `ibmq_qasm_simulator` was retired in 2024.

> **Note:** Real hardware backends queue jobs. Execution time is unpredictable and can range from seconds to tens of minutes depending on queue load. The [polling workflow](../example/getting-started/ibmq-example-workflow_polling.bpmn) should be utilized for all use cases except when there is an exclusive reservation for a quantum backend.

---

## 2. Camunda Prerequisites

### Cluster credentials

For Camunda SaaS, find your cluster connection details in the [Camunda Console](https://console.camunda.io/):

1. Open your cluster → **API** tab → **Create new client credentials**.
2. Note the **Client ID**, **Client Secret**, **Cluster ID**, **Region**, as well as the **grpc-address** and **rest-address**.

For self-managed Camunda, use the gRPC address of your Zeebe gateway.

### Store IBM Quantum credentials as Connector Secrets

When running the IBM Quantum Connector within the Camunda Connector Runtime, the IBMQ credentials can be embedded within the utilized Camunda Cluster Connector Secrets:

1. In the Camunda Console, go to your cluster → **Secrets**.
2. Create two secrets:

| Secret name | Value |
|---|---|
| `IBMQ_API_KEY` | Your IBM Cloud API key |
| `IBMQ_INSTANCE` | Your IBM Quantum instance CRN |

Alternatively, when self-hosting the IBM Quantum Connector the IBMQ credentials have to be stored as environment variables using the same names as described above.

Reference them in the start form when initiating a process instance using the `{{secrets.<name>}}` syntax:

```
IBM Quantum API Key:  {{secrets.IBMQ_API_KEY}}
IBM Quantum Instance: {{secrets.IBMQ_INSTANCE}}
```

---

## 3. First Workflow Execution

### Start the connector

After configuring `application.properties` as described in the [README](../README.md#1-configure-the-connector), run the connector using either the pre-built JAR or from source — see [README § Run the Connector](../README.md#2-run-the-connector) for both options.

You should see the connector register as a Zeebe job worker:

```
INFO  IBMQConnectorApplication - Started IBMQConnectorApplication
```

### Deploy and start the example workflow

The connector can deploy the example workflows automatically on startup:

```properties
ibmq.example.deploy=true
```

Or deploy manually by uploading these files to your Camunda cluster via the Web Modeler:

- `example/getting-started/ibmq-example-workflow_polling.bpmn`
- `example/getting-started/ibmq-input-form.form`
- `example/getting-started/ibmq-result-form.form`

Start a process instance via **Camunda Tasklist** and fill in the start form:

| Field | Example value |
|---|---|
| IBM Quantum API Key | `{{secrets.IBMQ_API_KEY}}` |
| IBMQ URL | `https://quantum.cloud.ibm.com/api` |
| IBMQ Instance CRN | `{{secrets.IBMQ_INSTANCE}}` |
| Backend | `ibm_kingston` |
| Quantum Circuit (OpenQASM) | see below |
| Shots | `1024` |

A minimal single-qubit circuit to test with:

```
OPENQASM 3.0;
include "stdgates.inc";
qubit[1] q;
bit[1] c;
h q[0];
c[0] = measure q[0];
```
> **Note:** The online transpilation feature was removed from IBM Quantum Platform. Thus, a [circuit generating sidecar](use-predefined-algorithms.md) should be used. Alternatively the quantum circuit can be [pre-transpiled locally using Qiskit](https://quantum.cloud.ibm.com/docs/en/guides/transpile).

### Review the result

Once the job completes, a **Review Result** user task appears in Tasklist.
The result contains:

| Variable | Description |
|---|---|
| `ibmqResult.jobId` | IBM Quantum job ID |
| `ibmqResult.status` | Final status (`COMPLETED`, `FAILED`, …) |
| `ibmqResult.result` | Raw Qiskit Runtime result payload |
| `ibmqResult.resultUrl` | Link to the job in the IBM Quantum UI |

---

## 4. Common Pitfalls

- **Job stays QUEUED for a long time**:
  Real hardware backends have queues.
  The number of pending jobs for each quantum computer can be checked via the [IBM Quantum Platform](https://quantum.cloud.ibm.com/computers).
  The polling workflow handles this correctly — do not reduce the timeout or the connector will mark the job as failed before it runs.

- **`secrets.IBMQ_API_KEY` is not resolved**:
  Secrets are only resolved by the Camunda Engine, not by the connector directly.
  Ensure the secret is created in the Camunda Console for the correct cluster.

- **`Circuit 0: The instruction X on qubits (Y) is not supported by the target system`**:
  The submitted circuit uses gates not natively supported by the selected backend.
  Transpile the circuit for the target backend using Qiskit before submitting, or use the [IBM Quantum Algorithm Accelerator pattern](/docs/use-predefined-algorithms.md) which handles transpilation automatically.

- **Connector does not pick up jobs after a Camunda SaaS outage**:
  The connector sends periodic gRPC keepalive pings (`camunda.client.keep-alive=PT30S`) and uses a Docker health check against `/actuator/health` to detect lost connections.
  If jobs are still stuck, restart the connector container — Zeebe will re-activate any jobs whose timeout has elapsed.
