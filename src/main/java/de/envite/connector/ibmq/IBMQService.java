package de.envite.connector.ibmq;

import static de.envite.connector.ibmq.IBMQConstants.STATUS_COMPLETED;
import static de.envite.connector.ibmq.IBMQConstants.STATUS_QUEUED;
import static de.envite.connector.ibmq.IBMQConstants.UI_PATH_INSTANCES;
import static de.envite.connector.ibmq.IBMQConstants.UI_PATH_JOBS;

import de.envite.connector.ibmq.dto.IBMQBaseRequest;
import de.envite.connector.ibmq.dto.IBMQConnectorResponseDto;
import de.envite.connector.ibmq.dto.IBMQGetJobResultRequestDto;
import de.envite.connector.ibmq.dto.IBMQSubmitJobRequestDto;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
  public IBMQConnectorResponseDto getJobResult(IBMQGetJobResultRequestDto request) {
    log.debug("[IBMQService] Checking job result: id={}", request.getJobId());
    String accessToken = authenticator.getAccessToken(request.getApiKey());
    String status = jobClient.getJobStatus(request, accessToken, request.getJobId());
    log.debug("[IBMQService] Job status: id={} status={}", request.getJobId(), status);
    Object result = STATUS_COMPLETED.equals(status)
        ? jobClient.getJobResults(request, accessToken, request.getJobId()) : null;
    return new IBMQConnectorResponseDto(request.getJobId(), status, result,
        buildResultUrl(request, request.getJobId()));
  }

  /**
   * Authenticates with IBM Cloud, submits a Qiskit Runtime job, and optionally waits for its result.
   *
   * <p>When {@link IBMQSubmitJobRequestDto#getWaitForResult()} is {@code false}, the job is submitted
   * and the response is returned immediately with status {@code QUEUED} and no result payload.
   * Otherwise, the method polls until the job reaches a terminal state or the configured timeout
   * is exceeded.</p>
   *
   * @param request the connector request describing the job to run
   * @return the job ID, terminal status, and result (if completed and waited for)
   * @throws RuntimeException if the job times out, polling is interrupted, or any HTTP call fails
   */
  public IBMQConnectorResponseDto executeCircuit(IBMQSubmitJobRequestDto request) {
    log.debug("[IBMQService] Received request: {}", request);
    String accessToken = authenticator.getAccessToken(request.getApiKey());
    log.debug("[IBMQService] Successfully authenticated at IBMQ");
    String jobId = jobClient.submitJob(request, accessToken, parameterHandler.buildParams(request));
    log.debug("[IBMQService] Job submitted: id={} backend={} program={}", jobId,
        request.getBackend(), request.getProgramId());

    if (!request.getWaitForResult()) {
      log.debug("[IBMQService] waitForResult=false, returning immediately with status QUEUED");
      return new IBMQConnectorResponseDto(jobId, STATUS_QUEUED, null,
          buildResultUrl(request, jobId));
    }

    String status = jobClient.pollUntilTerminal(request, accessToken, jobId);
    log.debug("[IBMQService] Job reached terminal state: id={} status={}", jobId, status);
    Object result =
        STATUS_COMPLETED.equals(status) ? jobClient.getJobResults(request, accessToken, jobId) :
            null;

    return new IBMQConnectorResponseDto(jobId, status, result, buildResultUrl(request, jobId));
  }

  /**
   * Constructs the IBM Quantum web UI URL for a given job.
   *
   * <p>The web base is derived from the API URL by retaining only the scheme and host
   * (e.g. {@code https://quantum.cloud.ibm.com/api} → {@code https://quantum.cloud.ibm.com}).
   * The instance CRN is URL-encoded before being embedded in the path.</p>
   *
   * @param request request carrying the API URL and instance CRN
   * @param jobId   ID of the job to link to
   * @return the full URL to the job details page in the IBM Quantum web UI
   */
  private String buildResultUrl(IBMQBaseRequest request, String jobId) {
    URI apiUri = URI.create(request.getIbmqUrl());
    String webBase = apiUri.getScheme() + "://" + apiUri.getHost();
    String encodedCrn = URLEncoder.encode(request.getIbmqInstance(), StandardCharsets.UTF_8);
    return webBase + UI_PATH_INSTANCES + encodedCrn + UI_PATH_JOBS + jobId;
  }
}
