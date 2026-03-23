package de.envite.connector.ibmq;

import de.envite.connector.ibmq.dto.IBMQBaseRequest;
import de.envite.connector.ibmq.dto.IBMQConnectorResponse;
import de.envite.connector.ibmq.dto.IBMQGetJobResultRequest;
import de.envite.connector.ibmq.dto.IBMQSubmitJobRequest;

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
    void execute_withSubmitJob_delegatesToExecuteCircuit() {
        IBMQSubmitJobRequest submitRequest = buildOpenQasmRequest();
        IBMQConnectorResponse expected = new IBMQConnectorResponse("job-123", STATUS_COMPLETED, null);

        OutboundConnectorContext context = mock(OutboundConnectorContext.class);
        when(context.bindVariables(IBMQBaseRequest.class)).thenReturn(baseRequest(OperationMode.SUBMIT_JOB));
        when(context.bindVariables(IBMQSubmitJobRequest.class)).thenReturn(submitRequest);
        when(ibmqService.executeCircuit(submitRequest)).thenReturn(expected);

        Object result = function.execute(context);

        assertThat(result).isEqualTo(expected);
        verify(ibmqService).executeCircuit(submitRequest);
        verify(ibmqService, never()).getJobResult(any());
    }

    @Test
    void execute_withGetJobResult_delegatesToGetJobResult() {
        IBMQGetJobResultRequest getRequest = buildGetJobResultRequest();
        IBMQConnectorResponse expected = new IBMQConnectorResponse("job-456", STATUS_COMPLETED, null);

        OutboundConnectorContext context = mock(OutboundConnectorContext.class);
        when(context.bindVariables(IBMQBaseRequest.class)).thenReturn(baseRequest(OperationMode.GET_JOB_RESULT));
        when(context.bindVariables(IBMQGetJobResultRequest.class)).thenReturn(getRequest);
        when(ibmqService.getJobResult(getRequest)).thenReturn(expected);

        Object result = function.execute(context);

        assertThat(result).isEqualTo(expected);
        verify(ibmqService).getJobResult(getRequest);
        verify(ibmqService, never()).executeCircuit(any());
    }

    @Test
    void execute_withDirectParams_delegatesToExecuteCircuit() {
        IBMQSubmitJobRequest submitRequest = buildDirectParamsRequest();
        IBMQConnectorResponse expected = new IBMQConnectorResponse("job-456", STATUS_COMPLETED, null);

        OutboundConnectorContext context = mock(OutboundConnectorContext.class);
        when(context.bindVariables(IBMQBaseRequest.class)).thenReturn(baseRequest(OperationMode.SUBMIT_JOB));
        when(context.bindVariables(IBMQSubmitJobRequest.class)).thenReturn(submitRequest);
        when(ibmqService.executeCircuit(submitRequest)).thenReturn(expected);

        Object result = function.execute(context);

        assertThat(result).isEqualTo(expected);
        verify(ibmqService).executeCircuit(submitRequest);
    }

    @Test
    void execute_passesAllBoundFieldsToService() {
        IBMQSubmitJobRequest submitRequest = buildOpenQasmRequest();

        OutboundConnectorContext context = mock(OutboundConnectorContext.class);
        when(context.bindVariables(IBMQBaseRequest.class)).thenReturn(baseRequest(OperationMode.SUBMIT_JOB));
        when(context.bindVariables(IBMQSubmitJobRequest.class)).thenReturn(submitRequest);
        when(ibmqService.executeCircuit(any())).thenReturn(new IBMQConnectorResponse("job-789", STATUS_QUEUED, null));

        function.execute(context);

        ArgumentCaptor<IBMQSubmitJobRequest> captor = ArgumentCaptor.forClass(IBMQSubmitJobRequest.class);
        verify(ibmqService).executeCircuit(captor.capture());

        IBMQSubmitJobRequest captured = captor.getValue();
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
        IBMQSubmitJobRequest submitRequest = buildOpenQasmRequest();
        submitRequest.setWaitForResult(false);
        IBMQConnectorResponse queuedResult = new IBMQConnectorResponse("job-123", STATUS_QUEUED, null);

        OutboundConnectorContext context = mock(OutboundConnectorContext.class);
        when(context.bindVariables(IBMQBaseRequest.class)).thenReturn(baseRequest(OperationMode.SUBMIT_JOB));
        when(context.bindVariables(IBMQSubmitJobRequest.class)).thenReturn(submitRequest);
        when(ibmqService.executeCircuit(submitRequest)).thenReturn(queuedResult);

        IBMQConnectorResponse result = (IBMQConnectorResponse) function.execute(context);

        assertThat(result.getStatus()).isEqualTo(STATUS_QUEUED);
        assertThat(result.getResult()).isNull();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private IBMQBaseRequest baseRequest(OperationMode mode) {
        IBMQBaseRequest base = new IBMQBaseRequest();
        base.setOperationMode(mode);
        return base;
    }

    private IBMQSubmitJobRequest buildOpenQasmRequest() {
        IBMQSubmitJobRequest request = new IBMQSubmitJobRequest();
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

    private IBMQSubmitJobRequest buildDirectParamsRequest() {
        IBMQSubmitJobRequest request = new IBMQSubmitJobRequest();
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

    private IBMQGetJobResultRequest buildGetJobResultRequest() {
        IBMQGetJobResultRequest request = new IBMQGetJobResultRequest();
        request.setApiKey("test-key");
        request.setIbmqUrl("https://quantum.cloud.ibm.com/api");
        request.setIbmqInstance("ibm-q/open/main");
        request.setJobId("job-456");
        return request;
    }
}
