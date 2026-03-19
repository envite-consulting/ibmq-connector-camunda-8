package de.envite.connector.ibmq;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import static de.envite.connector.ibmq.IBMQConstants.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Workflow integration tests for the IBMQ connector.
 *
 * <p>Deploys a real BPMN process to an embedded Camunda engine, starts a process
 * instance with quantum circuit input variables, and asserts that the process
 * completes with the expected output variable set by the connector.</p>
 *
 * <p>This tests the full Camunda integration path: job worker activation, element
 * template variable bindings, connector execution, and result variable mapping —
 * none of which are covered by {@link IBMQServiceTest} or
 * {@link IBMQConnectorFunctionTest}.</p>
 */
@Tag("workflow")
@CamundaSpringProcessTest
@SpringBootTest
class IBMQWorkflowTest {

    private static final String SERVICE_URL = "https://test.quantum-computing.ibm.com";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String JOB_ID = "workflow-job-001";
    private static final String CIRCUIT = "OPENQASM 3.0; qubit[1] q; h q[0];";

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        camundaClient.newDeployResourceCommand()
                .addResourceFromClasspath("ibmq_circuit_execution.bpmn")
                .send()
                .join();

        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @AfterEach
    void tearDown() {
        mockServer.reset();
    }

    @Test
    void openQasmSamplerJob_processCompletesWithResult() {
        expectIamTokenExchange();
        expectJobSubmission(JOB_ID);
        expectJobStatus(JOB_ID, STATUS_COMPLETED);
        expectJobResults(JOB_ID);

        ProcessInstanceEvent instance = startProcess(Map.ofEntries(
                Map.entry("apiKey",              "test-api-key"),
                Map.entry("ibmqUrl",             SERVICE_URL),
                Map.entry("ibmqInstance",        "ibm-q/open/main"),
                Map.entry("backend",             "ibmq_qasm_simulator"),
                Map.entry("programId",           PROGRAM_SAMPLER),
                Map.entry("CircuitInputMode",    "OPEN_QASM"),
                Map.entry("circuit",             CIRCUIT),
                Map.entry("shots",               1024),
                Map.entry("waitForResult",       true),
                Map.entry("timeoutSeconds",      30),
                Map.entry("pollIntervalSeconds", 1)
        ));

        CamundaAssert.assertThatProcessInstance(instance)
                .isCompleted()
                .hasVariableNames("ibmqResult");
    }

    @Test
    void directParamsEstimatorJob_processCompletesWithResult() {
        expectIamTokenExchange();
        expectJobSubmission(JOB_ID);
        expectJobStatus(JOB_ID, STATUS_COMPLETED);
        expectJobResults(JOB_ID);

        ProcessInstanceEvent instance = startProcess(Map.ofEntries(
                Map.entry("apiKey",              "test-api-key"),
                Map.entry("ibmqUrl",             SERVICE_URL),
                Map.entry("ibmqInstance",        "ibm-q/open/main"),
                Map.entry("backend",             "ibm_brisbane"),
                Map.entry("programId",           PROGRAM_ESTIMATOR),
                Map.entry("CircuitInputMode",    "DIRECT_PARAMS"),
                Map.entry("params",              "{\"pubs\": [[\"circuit\", [\"ZZ\"], null]]}"),
                Map.entry("waitForResult",       true),
                Map.entry("timeoutSeconds",      30),
                Map.entry("pollIntervalSeconds", 1)
        ));

        CamundaAssert.assertThatProcessInstance(instance)
                .isCompleted()
                .hasVariableNames("ibmqResult");
    }

    @Test
    void samplerJob_withNoWait_processCompletesWithQueuedStatus() {
        expectIamTokenExchange();
        expectJobSubmission(JOB_ID);

        ProcessInstanceEvent instance = startProcess(Map.ofEntries(
                Map.entry("apiKey",              "test-api-key"),
                Map.entry("ibmqUrl",             SERVICE_URL),
                Map.entry("ibmqInstance",        "ibm-q/open/main"),
                Map.entry("backend",             "ibmq_qasm_simulator"),
                Map.entry("programId",           PROGRAM_SAMPLER),
                Map.entry("CircuitInputMode",    "OPEN_QASM"),
                Map.entry("circuit",             CIRCUIT),
                Map.entry("shots",               512),
                Map.entry("waitForResult",       false),
                Map.entry("timeoutSeconds",      30),
                Map.entry("pollIntervalSeconds", 1)
        ));

        CamundaAssert.assertThatProcessInstance(instance)
                .isCompleted()
                .hasVariableSatisfies("ibmqResult", Map.class, result ->
                        assertThat(result.get("status")).isEqualTo(STATUS_QUEUED));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ProcessInstanceEvent startProcess(Map<String, Object> variables) {
        return camundaClient.newCreateInstanceCommand()
                .bpmnProcessId("ibmq-circuit-execution")
                .latestVersion()
                .variables(variables)
                .send()
                .join();
    }

    private void expectIamTokenExchange() {
        mockServer.expect(requestTo(IAM_TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"access_token": "%s", "expires_in": 3600}
                        """.formatted(ACCESS_TOKEN),
                        MediaType.APPLICATION_JSON));
    }

    private void expectJobSubmission(String jobId) {
        mockServer.expect(requestTo(SERVICE_URL + PATH_JOBS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andRespond(withSuccess(
                        """
                        {"id": "%s", "status": "QUEUED"}
                        """.formatted(jobId),
                        MediaType.APPLICATION_JSON));
    }

    private void expectJobStatus(String jobId, String status) {
        mockServer.expect(requestTo(SERVICE_URL + PATH_JOBS + "/" + jobId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"id": "%s", "status": "%s"}
                        """.formatted(jobId, status),
                        MediaType.APPLICATION_JSON));
    }

    private void expectJobResults(String jobId) {
        mockServer.expect(requestTo(SERVICE_URL + PATH_JOBS + "/" + jobId + PATH_RESULTS))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"quasi_dists": [{"0": 0.5, "1": 0.5}], "metadata": [{"shots": 1024}]}
                        """,
                        MediaType.APPLICATION_JSON));
    }
}
