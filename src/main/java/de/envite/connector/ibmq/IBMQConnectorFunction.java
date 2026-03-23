package de.envite.connector.ibmq;

import de.envite.connector.ibmq.dto.IBMQBaseRequest;
import de.envite.connector.ibmq.dto.IBMQGetJobResultRequest;
import de.envite.connector.ibmq.dto.IBMQSubmitJobRequest;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import org.springframework.stereotype.Component;

@OutboundConnector(
        name = "IBMQ",
        inputVariables = {
                "operationMode",
                "apiKey",
                "ibmqUrl",
                "ibmqInstance",
                "jobId",
                "backend",
                "programId",
                "CircuitInputMode",
                "circuit",
                "qasmVersion",
                "shots",
                "params",
                "waitForResult",
                "timeoutSeconds",
                "pollIntervalSeconds"
        },
        type = "de.envite:ibmq-connector:1"
)
@Component
public class IBMQConnectorFunction implements OutboundConnectorFunction {

    private final IBMQService ibmqService;

    public IBMQConnectorFunction(IBMQService ibmqService) {
        this.ibmqService = ibmqService;
    }

    @Override
    public Object execute(OutboundConnectorContext context) {
        OperationMode mode = context.bindVariables(IBMQBaseRequest.class).getOperationMode();
        return switch (mode) {
            case SUBMIT_JOB     -> ibmqService.executeCircuit(context.bindVariables(IBMQSubmitJobRequest.class));
            case GET_JOB_RESULT -> ibmqService.getJobResult(context.bindVariables(IBMQGetJobResultRequest.class));
        };
    }
}
