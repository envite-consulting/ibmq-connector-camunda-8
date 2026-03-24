package de.envite.connector.ibmq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Output returned by the IBMQ connector after job execution.
 *
 * <p>Always contains the job ID and its terminal status. The result payload is populated
 * only when {@code waitForResult} was {@code true} and the job completed successfully.</p>
 */
@Data
@AllArgsConstructor
public class IBMQConnectorResponseDto {

    /** IBM Quantum job identifier. */
    private String jobId;

    /** Terminal job status: COMPLETED, FAILED, CANCELLED. */
    private String status;

    /**
     * Job results returned by the IBM Quantum API.
     * <code>null</code> when <code>waitForResult</code> is <code>false</code>
     * or the job has not yet completed.
     */
    private Object result;

    /** Link to the job details page in the IBM Quantum web UI. */
    private String resultUrl;
}
