package de.envite.connector.ibmq;

import de.envite.connector.ibmq.dto.IBMQConnectorRequest;
import de.envite.connector.ibmq.dto.IBMQConnectorResponse;

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
     * Authenticates with IBM Cloud, submits a Qiskit Runtime job, and optionally waits for its result.
     *
     * <p>When {@link IBMQConnectorRequest#getWaitForResult()} is {@code false}, the job is submitted
     * and the response is returned immediately with status {@code QUEUED} and no result payload.
     * Otherwise, the method polls until the job reaches a terminal state or the configured timeout
     * is exceeded.</p>
     *
     * @param request the connector request describing the job to run
     * @return the job ID, terminal status, and result (if completed and waited for)
     * @throws RuntimeException if the job times out, polling is interrupted, or any HTTP call fails
     */
    public IBMQConnectorResponse executeCircuit(IBMQConnectorRequest request) {
        log.debug("[IBMQService] Received request: {}", request);
        String accessToken = authenticator.getAccessToken(request.getApiKey());
        String jobId = jobClient.submitJob(request, accessToken, parameterHandler.buildParams(request));
        log.debug("[IBMQService] Job submitted: id={} backend={} program={}", jobId, request.getBackend(), request.getProgramId());

        if (!request.getWaitForResult()) {
            log.debug("[IBMQService] waitForResult=false, returning immediately with status QUEUED");
            return new IBMQConnectorResponse(jobId, STATUS_QUEUED, null);
        }

        String status = jobClient.pollUntilTerminal(request, accessToken, jobId);
        log.debug("[IBMQService] Job reached terminal state: id={} status={}", jobId, status);
        Object result = STATUS_COMPLETED.equals(status) ? jobClient.getJobResults(request.getIbmqUrl(), accessToken, jobId) : null;

        return new IBMQConnectorResponse(jobId, status, result);
    }
}
