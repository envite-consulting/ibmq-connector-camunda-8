# IBM Quantum Connector for Camunda 8 ⚛️

*Run quantum circuits from your BPMN workflow — on real IBM Quantum hardware 🚀*

[![Build](https://github.com/envite-consulting/ibmq-connector-camunda-8/actions/workflows/build.yml/badge.svg)](https://github.com/envite-consulting/ibmq-connector-camunda-8/actions/workflows/build.yml)
[![Compatible with: Camunda Platform 8](https://img.shields.io/badge/Compatible%20with-Camunda%20Platform%208-26d07c)](https://docs.camunda.io/)
[![Camunda Marketplace](https://img.shields.io/badge/Find_on-Camunda_Marketplace-brightgreen?style=flat&color=orange)](https://marketplace.camunda.com/en-US/listing?q=IBMQ&page=1)
[![sponsored](https://img.shields.io/badge/sponsoredBy-envite-g.svg)](https://envite.de/)
[![Apache 2.0 License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](/LICENSE.md)

The **IBM Quantum Connector** is a [Camunda 8 outbound connector](https://docs.camunda.io/docs/components/connectors/introduction-to-connectors/) that integrates quantum computing into BPMN workflows by submitting and polling jobs on [IBM Quantum](https://quantum.cloud.ibm.com/) backends via the Qiskit Runtime API.
It supports both Sampler and Estimator primitives, accepts quantum circuits as OpenQASM strings or raw JSON parameters, and offers blocking and non-blocking (polling) execution patterns to handle the unpredictable queue times of real quantum hardware.
For higher-level algorithms — such as Grover's search algorithm or Quantum Approximate Optimization Algorithm (QAOA) — the [IBM Quantum Algorithm Accelerator pattern](docs/use-predefined-algorithms.md) extends the connector with a lightweight Python sidecar
This sidecar generates transpiled circuits from classical problem inputs and interprets raw measurement results, enabling the execution of variational quantum algorithms with classical optimization loops within a BPMN workflow.

In addition to the exemplary workflows orchestrating complex quantum algorithms, two simpel example workflows are provided in the `example/getting-started/` directory:

- **[Blocking](example/getting-started/ibmq-example-workflow_blocking.bpmn)** — submits a circuit and blocks the connector thread until the job reaches a terminal state (`waitForResult=true`). This is simple to use, but quantum jobs on real hardware backends may queue for longer than the configured timeout, causing the connector to throw a timeout exception and Camunda to re-execute the circuit. Further, this can lead to an incidents if all retries are used.
- **[Polling](example/getting-started/ibmq-example-workflow_polling.bpmn)** — submits the job without waiting (`waitForResult=false`), then polls the result every 30 seconds via a BPMN timer loop using the `GET_JOB_RESULT` operation. Recommended for real hardware backends where execution time is unpredictable.

Both workflows include a start event with an input form for all relevant connector parameters, the IBM Quantum Connector service task, a user task for reviewing the result, and an end event.
The polling example workflow can be seen below.
In the first step of the process, the quantum circuit is submitted using the IBM Quantum Connector, afterward a loop is entered, checking for the current state of the quantum job every 30s using the connector until it reaches a terminated state (completed, canceled, error).
To run the example, follow the steps under [How to Run](#-how-to-run), and then import the file into Camunda Modeler.

![Example workflow in Camunda Modeler](docs/images/example-workflow-polling.png)

---

# Table of Contents

* 🚀 [How to Run](#-how-to-run)
* 📚 [Connector Documentation](#-connector-documentation)
    * [Getting Started](docs/getting-started.md)
    * [Connector Configuration and Output Reference](docs/connector-reference.md)
    * [Using Predefined Quantum Algorithms via a Sidecar](docs/use-predefined-algorithms.md)
    * [Example Use Cases & HowTos](docs/usecases.md)
* 🛠️ [Development and Project Setup](#️-development-and-project-setup)

---

## 🚀 How to Run

### Prerequisites

- **Java 21**
- **Maven 3.8+** (only required when building from source)
- A running **Camunda 8** instance (SaaS or Self-Managed)
- An **IBM Quantum** account with an API key (the key can be obtained [here](https://quantum.cloud.ibm.com/))

### 1. Configure the Connector

Create an `application.properties` file with your Camunda 8 connection details:

```properties
camunda.client.grpc-address=grpcs://<cluster-id>.<region>.zeebe.camunda.io:443
camunda.client.rest-address=https://<region>.zeebe.camunda.io/<cluster-id>
camunda.client.auth.client-id=<your-client-id>
camunda.client.auth.client-secret=<your-client-secret>
camunda.client.cloud.cluster-id=<cluster-id>
camunda.client.cloud.region=<region>
```

### 2. Run the Connector

**Option A — Pre-built JAR (recommended)**

Download the latest `ibmq-connector-camunda-8-*.jar` from the [GitHub Releases](https://github.com/envite-consulting/ibmq-connector-camunda-8/releases) page and place it in a directory alongside your `application.properties` file:

```
ibmq-connector/
├── ibmq-connector-camunda-8-1.0.0.jar
└── application.properties
```

Then run:

```bash
java -jar ibmq-connector-camunda-8-1.0.0.jar
```

> **Note:** The element template `ibmq-connector.json` is also available as a release artifact — download it instead of fetching it from the repository.

**Option B — Build from source**

```bash
mvn package
java -jar target/ibmq-connector-camunda-8-*.jar
```

When building from source, edit `src/main/resources/application.properties` directly before running.

---

The connector registers itself as a Camunda job worker and starts polling for jobs of type `de.envite:ibmq-connector:1`.

### 3. Import the Element Template

Import `element-templates/ibmq-connector.json` into your Camunda Modeler to get the pre-configured service task with all input fields and the quantum icon:

- **Camunda Web Modeler**: go to your project → *Create new* → *Upload files* → select `element-templates/ibmq-connector.json`. Afterward, open the element template and publish it to the project or organization.
- **Camunda Desktop Modeler**: copy the file into the `resources/element-templates` directory of the modeler.

### 4. Model and Deploy a Process

Example workflows are provided in `example/getting-started/` (see [above](#ibm-quantum-connector-for-camunda-8-) for a description of each).
The connector can automatically deploy the example workflows and their forms to your Camunda cluster on startup by enabling the following property in `application.properties`:

```properties
ibmq.example.deploy=true
```

> **Note:** Keep this set to `false` (the default) in production environments.

If you prefer to deploy manually, upload the desired workflow from `example/getting-started/` together with `example/getting-started/ibmq-input-form.form` and `example/getting-started/ibmq-result-form.form` to your cluster — either via Camunda Web Modeler or the Zeebe API.
In case you published the element template to a project, upload the workflow to the **same project** so Web Modeler automatically links the template and displays the connector with its icon.

To model your own process, add a service task and apply the **IBM Quantum Connector** element template, then fill in the required properties.
The full configuration and output reference can be found [here](docs/connector-reference.md).

## 📚 Connector Documentation

Learn how to effectively use the connectors in your processes:
* [Getting Started](docs/getting-started.md): Details of how to get started with the IBM Quantum Connector
* [Connector Configuration and Output Reference](docs/connector-reference.md): All configuration properties and the connector output fields available for use in result expressions and downstream tasks
* [Using Predefined Quantum Algorithms via a Sidecar](docs/use-predefined-algorithms.md): Architecture and integration guide for using a Qiskit sidecar to generate quantum circuits from classical problem inputs and post-process measurement results — including support for variational algorithms (VQE, QAOA) with classical optimizer loops.
* [Example Use Cases & HowTos](docs/usecases.md): End-to-end workflow examples including Grover's search algorithm

## 🛠️ Development and Project Setup

### Project Structure

```
src/main/java/de/envite/connector/ibmq/
├── IBMQConnectorApplication.java   # Spring Boot entry point
├── IBMQConnectorFunction.java      # Connector entry point — dispatches to IBMQService
├── IBMQService.java                # Core logic: submit job, poll result
├── IBMQJobClient.java              # IBM Quantum REST API client
├── IBMQAuthenticator.java          # IBM Cloud IAM token exchange
├── IBMQParameterHandler.java       # Circuit input normalisation (OpenQASM / Direct Params)
├── IBMQConstants.java              # Shared constants (API paths, headers, program IDs)
├── dto/                            # Request / response DTOs
├── model/                          # Model classes
├── util/                           # Shared utilities
└── deployment/                     # Auto-deploys example workflows on startup (optional)

element-templates/                  # Camunda element template (connector UI)
example/
├── getting-started/                # Simple blocking and polling example workflows
└── predefined-algorithms/          # Grover and QAOA end-to-end examples with sidecar
docs/                               # Extended documentation
```

### Build

```bash
mvn package
```

Produces a self-contained JAR in `target/`.

### Testing

Unit and service tests (no external dependencies):

```bash
mvn test
```

Workflow integration tests use [Testcontainers](https://testcontainers.com/) to spin up a real Camunda Engine in Docker and exercise the full connector path end-to-end:

```bash
mvn test -Dgroups=workflow
```

These are excluded from the default `mvn test` run and require a local Docker daemon.

## License

This project is developed under

[![Apache 2.0 License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](/LICENSE.md)

## Sponsors and Customers

[![sponsored](https://img.shields.io/badge/sponsoredBy-envite-g.svg)](https://envite.de/)
