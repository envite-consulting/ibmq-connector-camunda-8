package de.envite.connector.ibmq;

import de.envite.connector.ibmq.dto.IBMQConnectorResponse;
import de.envite.connector.ibmq.dto.IBMQGetJobResultRequest;
import de.envite.connector.ibmq.dto.IBMQSubmitJobRequest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static de.envite.connector.ibmq.IBMQConstants.*;

@Slf4j
@AllArgsConstructor
@Service
public class IBMQService {

    private final IBMQAuthenticator authenticator;
    private final IBMQJobClient jobClient;
    private final IBMQParameterHandler parameterHandler;

    /**
     * Checks the current status of a previously submitted job and returns its result if completed.
     *
     * <p>Makes a single status request — no polling. Intended for use inside a BPMN polling loop
     * where a timer intermediate event controls the wait between invocations.</p>
     *
     * @param request the connector request; must contain {@code jobId}, {@code apiKey},
     *                {@code ibmqUrl}, and {@code ibmqInstance}
     * @return the job ID, current status, and result payload (only when status is {@code COMPLETED})
     */
    public IBMQConnectorResponse getJobResult(IBMQGetJobResultRequest request) {
        log.debug("[IBMQService] Checking job result: id={}", request.getJobId());
        String accessToken = authenticator.getAccessToken(request.getApiKey());
        String status = jobClient.getJobStatus(request, accessToken, request.getJobId());
        log.debug("[IBMQService] Job status: id={} status={}", request.getJobId(), status);
        Object result = STATUS_COMPLETED.equals(status) ? jobClient.getJobResults(request, accessToken, request.getJobId()) : null;
        return new IBMQConnectorResponse(request.getJobId(), status, result);
    }

    /**
     * Authenticates with IBM Cloud, submits a Qiskit Runtime job, and optionally waits for its result.
     *
     * <p>When {@link IBMQSubmitJobRequest#getWaitForResult()} is {@code false}, the job is submitted
     * and the response is returned immediately with status {@code QUEUED} and no result payload.
     * Otherwise, the method polls until the job reaches a terminal state or the configured timeout
     * is exceeded.</p>
     *
     * @param request the connector request describing the job to run
     * @return the job ID, terminal status, and result (if completed and waited for)
     * @throws RuntimeException if the job times out, polling is interrupted, or any HTTP call fails
     */
    public IBMQConnectorResponse executeCircuit(IBMQSubmitJobRequest request) {
        log.debug("[IBMQService] Received request: {}", request);
        String accessToken = authenticator.getAccessToken(request.getApiKey());
        log.debug("[IBMQService] Successfully authenticated at IBMQ");
        String jobId = jobClient.submitJob(request, accessToken, parameterHandler.buildParams(request));
        log.debug("[IBMQService] Job submitted: id={} backend={} program={}", jobId, request.getBackend(), request.getProgramId());

        if (!request.getWaitForResult()) {
            log.debug("[IBMQService] waitForResult=false, returning immediately with status QUEUED");
            return new IBMQConnectorResponse(jobId, STATUS_QUEUED, null);
        }

        String status = jobClient.pollUntilTerminal(request, accessToken, jobId);
        log.debug("[IBMQService] Job reached terminal state: id={} status={}", jobId, status);
        Object result = STATUS_COMPLETED.equals(status) ? jobClient.getJobResults(request, accessToken, jobId) : null;

        return new IBMQConnectorResponse(jobId, status, result);
    }
}
