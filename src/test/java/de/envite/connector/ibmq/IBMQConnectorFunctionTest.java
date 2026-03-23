package de.envite.connector.ibmq;

import de.envite.connector.ibmq.dto.IBMQConnectorRequest;
import de.envite.connector.ibmq.dto.IBMQConnectorResponse;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static de.envite.connector.ibmq.IBMQConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("connector")
@ExtendWith(MockitoExtension.class)
class IBMQConnectorFunctionTest {

    @Mock
    private IBMQService ibmqService;

    @InjectMocks
    private IBMQConnectorFunction function;

    @Test
    void execute_withOpenQasmRequest_delegatesToServiceAndReturnsResult() {
        IBMQConnectorRequest request = buildOpenQasmRequest();
        IBMQConnectorResponse expectedResult = new IBMQConnectorResponse("job-123", STATUS_COMPLETED, null);

        OutboundConnectorContext context = mock(OutboundConnectorContext.class);
        when(context.bindVariables(IBMQConnectorRequest.class)).thenReturn(request);
        when(ibmqService.executeCircuit(request)).thenReturn(expectedResult);

        Object result = function.execute(context);

        assertThat(result).isEqualTo(expectedResult);
        verify(ibmqService).executeCircuit(request);
    }

    @Test
    void execute_withDirectParamsRequest_delegatesToServiceAndReturnsResult() {
        IBMQConnectorRequest request = buildDirectParamsRequest();
        IBMQConnectorResponse expectedResult = new IBMQConnectorResponse("job-456", STATUS_COMPLETED, null);

        OutboundConnectorContext context = mock(OutboundConnectorContext.class);
        when(context.bindVariables(IBMQConnectorRequest.class)).thenReturn(request);
        when(ibmqService.executeCircuit(request)).thenReturn(expectedResult);

        Object result = function.execute(context);

        assertThat(result).isEqualTo(expectedResult);
        verify(ibmqService).executeCircuit(request);
    }

    @Test
    void execute_passesAllBoundFieldsToService() {
        IBMQConnectorRequest request = buildOpenQasmRequest();

        OutboundConnectorContext context = mock(OutboundConnectorContext.class);
        when(context.bindVariables(IBMQConnectorRequest.class)).thenReturn(request);
        when(ibmqService.executeCircuit(any())).thenReturn(new IBMQConnectorResponse("job-789", STATUS_QUEUED, null));

        function.execute(context);

        ArgumentCaptor<IBMQConnectorRequest> captor = ArgumentCaptor.forClass(IBMQConnectorRequest.class);
        verify(ibmqService).executeCircuit(captor.capture());

        IBMQConnectorRequest captured = captor.getValue();
        assertThat(captured.getApiKey()).isEqualTo("test-key");
        assertThat(captured.getIbmqInstance()).isEqualTo("ibm-q/open/main");
        assertThat(captured.getBackend()).isEqualTo("ibmq_qasm_simulator");
        assertThat(captured.getProgramId()).isEqualTo(PROGRAM_SAMPLER);
        assertThat(captured.getCircuitInputMode()).isEqualTo(CircuitInputMode.OPEN_QASM);
        assertThat(captured.getCircuit()).isEqualTo("OPENQASM 3.0; qubit[1] q; h q[0];");
        assertThat(captured.getShots()).isEqualTo(512);
    }

    @Test
    void execute_withNoWaitRequest_returnsQueuedResult() {
        IBMQConnectorRequest request = buildOpenQasmRequest();
        request.setWaitForResult(false);
        IBMQConnectorResponse queuedResult = new IBMQConnectorResponse("job-123", STATUS_QUEUED, null);

        OutboundConnectorContext context = mock(OutboundConnectorContext.class);
        when(context.bindVariables(IBMQConnectorRequest.class)).thenReturn(request);
        when(ibmqService.executeCircuit(request)).thenReturn(queuedResult);

        IBMQConnectorResponse result = (IBMQConnectorResponse) function.execute(context);

        assertThat(result.getStatus()).isEqualTo(STATUS_QUEUED);
        assertThat(result.getResult()).isNull();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private IBMQConnectorRequest buildOpenQasmRequest() {
        IBMQConnectorRequest request = new IBMQConnectorRequest();
        request.setApiKey("test-key");
        request.setIbmqUrl("https://quantum.cloud.ibm.com/api");
        request.setIbmqInstance("ibm-q/open/main");
        request.setBackend("ibmq_qasm_simulator");
        request.setProgramId(PROGRAM_SAMPLER);
        request.setCircuitInputMode(CircuitInputMode.OPEN_QASM);
        request.setCircuit("OPENQASM 3.0; qubit[1] q; h q[0];");
        request.setShots(512);
        request.setWaitForResult(true);
        request.setTimeoutSeconds(30);
        request.setPollIntervalSeconds(5);
        return request;
    }

    private IBMQConnectorRequest buildDirectParamsRequest() {
        IBMQConnectorRequest request = new IBMQConnectorRequest();
        request.setApiKey("test-key");
        request.setIbmqUrl("https://quantum.cloud.ibm.com/api");
        request.setIbmqInstance("ibm-q/open/main");
        request.setBackend("ibmq_qasm_simulator");
        request.setProgramId(PROGRAM_SAMPLER);
        request.setCircuitInputMode(CircuitInputMode.DIRECT_PARAMS);
        request.setParams("{\"pubs\": [[\"OPENQASM 3.0; qubit[1] q; h q[0];\", null, 1024]]}");
        request.setWaitForResult(true);
        request.setTimeoutSeconds(30);
        request.setPollIntervalSeconds(5);
        return request;
    }
}
