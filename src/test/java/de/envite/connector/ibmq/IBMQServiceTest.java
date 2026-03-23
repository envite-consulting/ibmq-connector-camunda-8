package de.envite.connector.ibmq;

import de.envite.connector.ibmq.dto.IBMQConnectorResponseDto;
import de.envite.connector.ibmq.dto.IBMQGetJobResultRequestDto;
import de.envite.connector.ibmq.dto.IBMQSubmitJobRequestDto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static de.envite.connector.ibmq.IBMQConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("service")
class IBMQServiceTest {

    private static final String SERVICE_URL   = "https://test.quantum-computing.ibm.com";
    private static final String API_KEY       = "test-api-key";
    private static final String ACCESS_TOKEN  = "test-access-token";
    private static final String INSTANCE_CRN  = "crn:v1:bluemix:public:quantum-computing:us-east:a/test-account/test-instance::";
    private static final String JOB_ID        = "test-job-123";
    private static final String CIRCUIT       = "OPENQASM 3.0; include \"stdgates.inc\"; qubit[1] q; h q[0];";

    private MockRestServiceServer mockServer;
    private IBMQService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new IBMQService(new IBMQAuthenticator(restTemplate), new IBMQJobClient(restTemplate, objectMapper), new IBMQParameterHandler(objectMapper));
    }

    // -------------------------------------------------------------------------
    // Happy-path: OPEN_QASM mode
    // -------------------------------------------------------------------------

    @Test
    void executeCircuit_withOpenQasm_andNoWait_returnsQueuedStatus() {
        expectIamTokenExchange();
        expectJobSubmission();

        IBMQConnectorResponseDto result = service.executeCircuit(openQasmRequest().waitForResult(false).build());

        assertThat(result.getJobId()).isEqualTo(JOB_ID);
        assertThat(result.getStatus()).isEqualTo(STATUS_QUEUED);
        assertThat(result.getResult()).isNull();
        mockServer.verify();
    }

    @Test
    void executeCircuit_withOpenQasm_andWaitForResult_returnsCompletedResult() {
        expectIamTokenExchange();
        expectJobSubmission();
        expectJobStatus(STATUS_COMPLETED);
        expectJobResults();

        IBMQConnectorResponseDto result = service.executeCircuit(openQasmRequest().build());

        assertThat(result.getJobId()).isEqualTo(JOB_ID);
        assertThat(result.getStatus()).isEqualTo(STATUS_COMPLETED);
        assertThat(result.getResult()).isNotNull();
        mockServer.verify();
    }

    @Test
    void executeCircuit_withOpenQasm_andJobFails_returnsFailedStatusWithNullResult() {
        expectIamTokenExchange();
        expectJobSubmission();
        expectJobStatus(STATUS_FAILED);

        IBMQConnectorResponseDto result = service.executeCircuit(openQasmRequest().build());

        assertThat(result.getStatus()).isEqualTo(STATUS_FAILED);
        assertThat(result.getResult()).isNull();
        mockServer.verify();
    }

    @Test
    void executeCircuit_withOpenQasm_submitsCorrectPubStructure() {
        expectIamTokenExchange();
        mockServer.expect(requestTo(SERVICE_URL + PATH_JOBS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "program_id": "sampler",
                          "backend": "ibmq_qasm_simulator",
                          "params": { "version": 2, "pubs": [["%s", null, 512]] }
                        }
                        """.formatted(CIRCUIT.replace("\"", "\\\""))))
                .andRespond(withSuccess(jobResponse(JOB_ID), MediaType.APPLICATION_JSON));
        expectJobStatus(STATUS_COMPLETED);
        expectJobResults();

        service.executeCircuit(openQasmRequest().shots(512).build());

        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // Happy-path: DIRECT_PARAMS mode
    // -------------------------------------------------------------------------

    @Test
    void executeCircuit_withDirectParams_andNoWait_submitsCustomParams() {
        String customParams = """
                {"pubs": [["custom-circuit", null, 2048]]}
                """;
        expectIamTokenExchange();
        mockServer.expect(requestTo(SERVICE_URL + PATH_JOBS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"params\": {\"pubs\": [[\"custom-circuit\", null, 2048]]}}"))
                .andRespond(withSuccess(jobResponse(JOB_ID), MediaType.APPLICATION_JSON));

        IBMQConnectorResponseDto result = service.executeCircuit(openQasmRequest()
                .circuitInputMode(CircuitInputMode.DIRECT_PARAMS)
                .params(customParams)
                .waitForResult(false)
                .build());

        assertThat(result.getJobId()).isEqualTo(JOB_ID);
        assertThat(result.getStatus()).isEqualTo(STATUS_QUEUED);
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // Polling: non-terminal status followed by terminal status
    // -------------------------------------------------------------------------

    @Test
    void executeCircuit_withOpenQasm_andIntermediateRunningStatus_pollsUntilCompleted() {
        expectIamTokenExchange();
        expectJobSubmission();
        expectJobStatus("RUNNING");
        expectJobStatus(STATUS_COMPLETED);
        expectJobResults();

        IBMQConnectorResponseDto result = service.executeCircuit(openQasmRequest().pollIntervalSeconds(1).build());

        assertThat(result.getStatus()).isEqualTo(STATUS_COMPLETED);
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // Timeout
    // -------------------------------------------------------------------------

    @Test
    @Timeout(5)
    void executeCircuit_whenJobDoesNotComplete_throwsTimeoutException() {
        expectIamTokenExchange();
        expectJobSubmission();
        mockServer.expect(manyTimes(), requestTo(SERVICE_URL + PATH_JOBS + "/" + JOB_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(statusResponse("RUNNING"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.executeCircuit(openQasmRequest().timeoutSeconds(1).pollIntervalSeconds(1).build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Timed out");
    }

    // -------------------------------------------------------------------------
    // Validation: OPEN_QASM mode
    // -------------------------------------------------------------------------

    @Test
    void executeCircuit_withOpenQasm_andBlankCircuit_throwsIllegalArgumentException() {
        expectIamTokenExchange();

        assertThatThrownBy(() -> service.executeCircuit(openQasmRequest().circuit("  ").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("circuit");
    }

    // -------------------------------------------------------------------------
    // Validation: DIRECT_PARAMS mode
    // -------------------------------------------------------------------------

    @Test
    void executeCircuit_withDirectParams_andBlankParams_throwsIllegalArgumentException() {
        expectIamTokenExchange();

        assertThatThrownBy(() -> service.executeCircuit(openQasmRequest()
                        .circuitInputMode(CircuitInputMode.DIRECT_PARAMS)
                        .params("  ")
                        .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("params");
    }

    @Test
    void executeCircuit_withDirectParams_andInvalidJson_throwsRuntimeException() {
        expectIamTokenExchange();

        assertThatThrownBy(() -> service.executeCircuit(openQasmRequest()
                        .circuitInputMode(CircuitInputMode.DIRECT_PARAMS)
                        .params("not-valid-json{{{")
                        .build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("parse");
    }

    // -------------------------------------------------------------------------
    // Get job result
    // -------------------------------------------------------------------------

    @Test
    void getJobResult_whenJobCompleted_returnsResultPayload() {
        expectIamTokenExchange();
        expectJobStatus(STATUS_COMPLETED);
        expectJobResults();

        IBMQConnectorResponseDto result = service.getJobResult(getJobResultRequest().build());

        assertThat(result.getJobId()).isEqualTo(JOB_ID);
        assertThat(result.getStatus()).isEqualTo(STATUS_COMPLETED);
        assertThat(result.getResult()).isNotNull();
        mockServer.verify();
    }

    @Test
    void getJobResult_whenJobRunning_returnsStatusWithoutResult() {
        expectIamTokenExchange();
        expectJobStatus("RUNNING");

        IBMQConnectorResponseDto result = service.getJobResult(getJobResultRequest().build());

        assertThat(result.getJobId()).isEqualTo(JOB_ID);
        assertThat(result.getStatus()).isEqualTo("RUNNING");
        assertThat(result.getResult()).isNull();
        mockServer.verify();
    }

    @Test
    void getJobResult_whenJobFailed_returnsFailedStatusWithoutResult() {
        expectIamTokenExchange();
        expectJobStatus(STATUS_FAILED);

        IBMQConnectorResponseDto result = service.getJobResult(getJobResultRequest().build());

        assertThat(result.getStatus()).isEqualTo(STATUS_FAILED);
        assertThat(result.getResult()).isNull();
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void expectIamTokenExchange() {
        mockServer.expect(requestTo(IAM_TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("apikey=" + API_KEY)))
                .andRespond(withSuccess(
                        "{\"access_token\": \"" + ACCESS_TOKEN + "\", \"expires_in\": 3600}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectJobSubmission() {
        mockServer.expect(requestTo(SERVICE_URL + PATH_JOBS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(header(HEADER_SERVICE_CRN, INSTANCE_CRN))
                .andExpect(header(HEADER_IBM_API_VERSION, IBM_API_VERSION))
                .andRespond(withSuccess(jobResponse(JOB_ID), MediaType.APPLICATION_JSON));
    }

    private void expectJobStatus(String status) {
        mockServer.expect(requestTo(SERVICE_URL + PATH_JOBS + "/" + JOB_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(header(HEADER_SERVICE_CRN, INSTANCE_CRN))
                .andExpect(header(HEADER_IBM_API_VERSION, IBM_API_VERSION))
                .andRespond(withSuccess(statusResponse(status), MediaType.APPLICATION_JSON));
    }

    private void expectJobResults() {
        mockServer.expect(requestTo(SERVICE_URL + PATH_JOBS + "/" + JOB_ID + PATH_RESULTS))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HEADER_SERVICE_CRN, INSTANCE_CRN))
                .andExpect(header(HEADER_IBM_API_VERSION, IBM_API_VERSION))
                .andRespond(withSuccess(
                        """
                        {"quasi_dists": [{"0": 0.5, "1": 0.5}], "metadata": [{"shots": 1024}]}
                        """,
                        MediaType.APPLICATION_JSON));
    }

    private static String jobResponse(String jobId) {
        return """
                {"id": "%s", "status": "QUEUED"}
                """.formatted(jobId);
    }

    private static String statusResponse(String status) {
        return """
                {"id": "%s", "status": "%s"}
                """.formatted(JOB_ID, status);
    }

    private IBMQSubmitJobRequestDto.IBMQSubmitJobRequestDtoBuilder<?, ?> openQasmRequest() {
        return IBMQSubmitJobRequestDto.builder()
                .apiKey(API_KEY)
                .ibmqUrl(SERVICE_URL)
                .ibmqInstance(INSTANCE_CRN)
                .backend("ibmq_qasm_simulator")
                .programId(PROGRAM_SAMPLER)
                .circuitInputMode(CircuitInputMode.OPEN_QASM)
                .circuit(CIRCUIT)
                .shots(1024)
                .waitForResult(true)
                .timeoutSeconds(30)
                .pollIntervalSeconds(1);
    }

    private IBMQGetJobResultRequestDto.IBMQGetJobResultRequestDtoBuilder<?, ?> getJobResultRequest() {
        return IBMQGetJobResultRequestDto.builder()
                .apiKey(API_KEY)
                .ibmqUrl(SERVICE_URL)
                .ibmqInstance(INSTANCE_CRN)
                .jobId(JOB_ID);
    }
}
