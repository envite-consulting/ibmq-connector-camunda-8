package de.envite.connector.ibmq.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Input parameters for the IBMQ connector when checking the result of a previously submitted job.
 *
 * <p>Makes a single status request with no polling. Intended for use inside a BPMN polling loop
 * driven by a timer intermediate event, as an alternative to blocking inside the connector.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IBMQGetJobResultRequest extends IBMQBaseRequest {

    /** ID of the previously submitted IBM Quantum job. */
    @NotEmpty
    private String jobId;
}
