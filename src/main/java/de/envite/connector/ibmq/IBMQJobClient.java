package de.envite.connector.ibmq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.envite.connector.ibmq.dto.IBMQConnectorRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Set;

import static de.envite.connector.ibmq.HttpHelper.requireBody;
import static de.envite.connector.ibmq.IBMQConstants.*;

/**
 * HTTP client for the IBM Quantum Runtime Jobs API.
 *
 * <p>Covers the full job lifecycle: submission, status polling, and result retrieval.
 * All requests are authenticated with an IBM Cloud IAM bearer token and must include
 * the {@code Service-CRN} and {@code IBM-API-Version} headers required by the API.</p>
 */
@Slf4j
@AllArgsConstructor
@Component
public class IBMQJobClient {

    private static final Set<String> TERMINAL_STATES = Set.of(STATUS_COMPLETED, STATUS_FAILED, STATUS_CANCELLED, STATUS_ERROR);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Submits a Qiskit Runtime job and returns its assigned job ID.
     *
     * @param request     request carrying the target URL, program ID, backend, and instance CRN
     * @param accessToken IBM Cloud IAM bearer token
     * @param params      pre-built job parameters (e.g. PUBs for sampler, observables for estimator)
     * @return the job ID assigned by the IBM Quantum runtime
     */
    public String submitJob(IBMQConnectorRequest request, String accessToken, JsonNode params) {
        ObjectNode jobBody = objectMapper.createObjectNode();
        jobBody.put(FIELD_PROGRAM_ID, request.getProgramId());
        jobBody.put(FIELD_BACKEND, request.getBackend());
        jobBody.set(FIELD_PARAMS, params);

        log.debug("[IBMQJobClient] Submitting job: URL={}, backend={}, programId={}", request.getIbmqUrl() + PATH_JOBS, request.getBackend(), request.getProgramId());
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                request.getIbmqUrl() + PATH_JOBS,
                new HttpEntity<>(jobBody, apiHeaders(accessToken, request.getIbmqInstance())),
                JsonNode.class
        );

        return requireBody(response, "job submission").get(FIELD_ID).asText();
    }

    /**
     * Polls the job status until it reaches a terminal state or the configured timeout expires.
     *
     * @param request     request carrying the target URL, instance CRN, timeout, and poll interval
     * @param accessToken IBM Cloud IAM bearer token
     * @param jobId       ID of the job to poll
     * @return the terminal status ({@code COMPLETED}, {@code FAILED}, {@code CANCELLED}, or {@code ERROR})
     * @throws RuntimeException if the timeout is exceeded or polling is interrupted
     */
    public String pollUntilTerminal(IBMQConnectorRequest request, String accessToken, String jobId) {
        Instant deadline = Instant.now().plusSeconds(request.getTimeoutSeconds());

        while (Instant.now().isBefore(deadline)) {
            String status = getJobStatus(request, accessToken, jobId);
            log.debug("[IBMQJobClient] Polled job status: id={} status={}", jobId, status);
            if (TERMINAL_STATES.contains(status)) {
                return status;
            }

            try {
                Thread.sleep(request.getPollIntervalSeconds() * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            }
        }

        throw new RuntimeException("Timed out after %d seconds waiting for job %s".formatted(
                request.getTimeoutSeconds(), jobId));
    }

    /**
     * Retrieves the results of a completed job.
     *
     * @param request     request carrying the target URL and instance CRN
     * @param accessToken IBM Cloud IAM bearer token
     * @param jobId       ID of the completed job
     * @return the raw result payload as a {@link JsonNode}
     */
    public Object getJobResults(IBMQConnectorRequest request, String accessToken, String jobId) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                request.getIbmqUrl() + PATH_JOBS + "/" + jobId + PATH_RESULTS,
                HttpMethod.GET,
                new HttpEntity<>(apiHeaders(accessToken, request.getIbmqInstance())),
                JsonNode.class
        );
        return requireBody(response, "job results");
    }

    /**
     * Fetches the current status of a job.
     */
    private String getJobStatus(IBMQConnectorRequest request, String accessToken, String jobId) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                request.getIbmqUrl() + PATH_JOBS + "/" + jobId,
                HttpMethod.GET,
                new HttpEntity<>(apiHeaders(accessToken, request.getIbmqInstance())),
                JsonNode.class
        );
        return requireBody(response, "job status").get(FIELD_STATUS).asText();
    }

    private HttpHeaders apiHeaders(String accessToken, String instanceCrn) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_SERVICE_CRN, instanceCrn);
        headers.set(HEADER_IBM_API_VERSION, IBM_API_VERSION);
        return headers;
    }
}
