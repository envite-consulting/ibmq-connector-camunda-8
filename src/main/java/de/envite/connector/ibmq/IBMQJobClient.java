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
 * All requests are authenticated with an IBM Cloud IAM bearer token obtained externally.</p>
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
     * @param request     request carrying the target URL, program ID, backend, and instance
     * @param accessToken IBM Cloud IAM bearer token
     * @param params      pre-built job parameters (e.g. PUBs for sampler, observables for estimator)
     * @return the job ID assigned by the IBM Quantum runtime
     */
    public String submitJob(IBMQConnectorRequest request, String accessToken, JsonNode params) {
        String[] instanceParts = parseInstance(request.getIbmqInstance());

        ObjectNode jobBody = objectMapper.createObjectNode();
        jobBody.put(FIELD_PROGRAM_ID, request.getProgramId());
        jobBody.put(FIELD_BACKEND, request.getBackend());
        jobBody.put(FIELD_HUB, instanceParts[0]);
        jobBody.put(FIELD_GROUP, instanceParts[1]);
        jobBody.put(FIELD_PROJECT, instanceParts[2]);
        jobBody.set(FIELD_PARAMS, params);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                request.getIbmqUrl() + PATH_JOBS,
                new HttpEntity<>(jobBody, bearerHeaders(accessToken)),
                JsonNode.class
        );

        return requireBody(response, "job submission").get(FIELD_ID).asText();
    }

    /**
     * Polls the job status until it reaches a terminal state or the configured timeout expires.
     *
     * @param request     request carrying the target URL, timeout, and poll interval
     * @param accessToken IBM Cloud IAM bearer token
     * @param jobId       ID of the job to poll
     * @return the terminal status ({@code COMPLETED}, {@code FAILED}, {@code CANCELLED}, or {@code ERROR})
     * @throws RuntimeException if the timeout is exceeded or polling is interrupted
     */
    public String pollUntilTerminal(IBMQConnectorRequest request, String accessToken, String jobId) {
        Instant deadline = Instant.now().plusSeconds(request.getTimeoutSeconds());

        while (Instant.now().isBefore(deadline)) {
            String status = getJobStatus(request.getIbmqUrl(), accessToken, jobId);
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
     * @param serviceUrl  base URL of the IBM Quantum runtime service
     * @param accessToken IBM Cloud IAM bearer token
     * @param jobId       ID of the completed job
     * @return the raw result payload as a {@link JsonNode}
     */
    public Object getJobResults(String serviceUrl, String accessToken, String jobId) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                serviceUrl + PATH_JOBS + "/" + jobId + PATH_RESULTS,
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(accessToken)),
                JsonNode.class
        );
        return requireBody(response, "job results");
    }

    /**
     * Fetches the current status of a job.
     *
     * @param serviceUrl  base URL of the IBM Quantum runtime service
     * @param accessToken IBM Cloud IAM bearer token
     * @param jobId       ID of the job to query
     * @return the job status string (e.g. {@code QUEUED}, {@code RUNNING}, {@code COMPLETED})
     */
    private String getJobStatus(String serviceUrl, String accessToken, String jobId) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                serviceUrl + PATH_JOBS + "/" + jobId,
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(accessToken)),
                JsonNode.class
        );
        return requireBody(response, "job status").get(FIELD_STATUS).asText();
    }

    /** Parses an instance string <code>hub/group/project</code> into its three parts. */
    private String[] parseInstance(String instance) {
        String[] parts = instance.split("/");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid instance format '%s'. Expected hub/group/project.".formatted(instance));
        }
        return parts;
    }

    private HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
