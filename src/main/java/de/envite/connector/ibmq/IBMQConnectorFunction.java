package de.envite.connector.ibmq;

import de.envite.connector.ibmq.dto.IBMQConnectorRequest;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import org.springframework.stereotype.Component;

@OutboundConnector(
        name = "IBMQ",
        inputVariables = {
                "apiKey",
                "ibmqUrl",
                "ibmqInstance",
                "backend",
                "programId",
                "circuitInputMode",
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
        IBMQConnectorRequest request = context.bindVariables(IBMQConnectorRequest.class);
        return ibmqService.executeCircuit(request);
    }
}
