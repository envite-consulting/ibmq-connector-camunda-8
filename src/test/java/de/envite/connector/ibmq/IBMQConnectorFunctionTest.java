package de.envite.connector.ibmq;

import static de.envite.connector.ibmq.IBMQConstants.PROGRAM_SAMPLER;
import static de.envite.connector.ibmq.IBMQConstants.STATUS_COMPLETED;
import static de.envite.connector.ibmq.IBMQConstants.STATUS_QUEUED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.envite.connector.ibmq.dto.IBMQBaseRequest;
import de.envite.connector.ibmq.dto.IBMQConnectorResponseDto;
import de.envite.connector.ibmq.dto.IBMQGetJobResultRequestDto;
import de.envite.connector.ibmq.dto.IBMQSubmitJobRequestDto;
import de.envite.connector.ibmq.model.CircuitInputMode;
import de.envite.connector.ibmq.model.OperationMode;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("connector")
@ExtendWith(MockitoExtension.class)
class IBMQConnectorFunctionTest {

  @Mock
  private IBMQService ibmqService;

  @InjectMocks
  private IBMQConnectorFunction function;

  @Test
  void execute_withSubmitJob_delegatesToExecuteCircuit() {
    IBMQSubmitJobRequestDto submitRequest = buildOpenQasmRequest();
    IBMQConnectorResponseDto expected =
        new IBMQConnectorResponseDto("job-123", STATUS_COMPLETED, null, null);

    OutboundConnectorContext context = mock(OutboundConnectorContext.class);
    when(context.bindVariables(IBMQBaseRequest.class)).thenReturn(
        baseRequest(OperationMode.SUBMIT_JOB));
    when(context.bindVariables(IBMQSubmitJobRequestDto.class)).thenReturn(submitRequest);
    when(ibmqService.executeCircuit(submitRequest)).thenReturn(expected);

    Object result = function.execute(context);

    assertThat(result).isEqualTo(expected);
    verify(ibmqService).executeCircuit(submitRequest);
    verify(ibmqService, never()).getJobResult(any());
  }

  @Test
  void execute_withGetJobResult_delegatesToGetJobResult() {
    IBMQGetJobResultRequestDto getRequest = buildGetJobResultRequest();
    IBMQConnectorResponseDto expected =
        new IBMQConnectorResponseDto("job-456", STATUS_COMPLETED, null, null);

    OutboundConnectorContext context = mock(OutboundConnectorContext.class);
    when(context.bindVariables(IBMQBaseRequest.class)).thenReturn(
        baseRequest(OperationMode.GET_JOB_RESULT));
    when(context.bindVariables(IBMQGetJobResultRequestDto.class)).thenReturn(getRequest);
    when(ibmqService.getJobResult(getRequest)).thenReturn(expected);

    Object result = function.execute(context);

    assertThat(result).isEqualTo(expected);
    verify(ibmqService).getJobResult(getRequest);
    verify(ibmqService, never()).executeCircuit(any());
  }

  @Test
  void execute_withDirectParams_delegatesToExecuteCircuit() {
    IBMQSubmitJobRequestDto submitRequest = buildDirectParamsRequest();
    IBMQConnectorResponseDto expected =
        new IBMQConnectorResponseDto("job-456", STATUS_COMPLETED, null, null);

    OutboundConnectorContext context = mock(OutboundConnectorContext.class);
    when(context.bindVariables(IBMQBaseRequest.class)).thenReturn(
        baseRequest(OperationMode.SUBMIT_JOB));
    when(context.bindVariables(IBMQSubmitJobRequestDto.class)).thenReturn(submitRequest);
    when(ibmqService.executeCircuit(submitRequest)).thenReturn(expected);

    Object result = function.execute(context);

    assertThat(result).isEqualTo(expected);
    verify(ibmqService).executeCircuit(submitRequest);
  }

  @Test
  void execute_passesAllBoundFieldsToService() {
    IBMQSubmitJobRequestDto submitRequest = buildOpenQasmRequest();

    OutboundConnectorContext context = mock(OutboundConnectorContext.class);
    when(context.bindVariables(IBMQBaseRequest.class)).thenReturn(
        baseRequest(OperationMode.SUBMIT_JOB));
    when(context.bindVariables(IBMQSubmitJobRequestDto.class)).thenReturn(submitRequest);
    when(ibmqService.executeCircuit(any())).thenReturn(
        new IBMQConnectorResponseDto("job-789", STATUS_QUEUED, null, null));

    function.execute(context);

    ArgumentCaptor<IBMQSubmitJobRequestDto> captor =
        ArgumentCaptor.forClass(IBMQSubmitJobRequestDto.class);
    verify(ibmqService).executeCircuit(captor.capture());

    IBMQSubmitJobRequestDto captured = captor.getValue();
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
    IBMQSubmitJobRequestDto submitRequest = IBMQSubmitJobRequestDto.builder()
        .apiKey("test-key")
        .ibmqUrl("https://quantum.cloud.ibm.com/api")
        .ibmqInstance("ibm-q/open/main")
        .backend("ibmq_qasm_simulator")
        .programId(PROGRAM_SAMPLER)
        .circuit("OPENQASM 3.0; qubit[1] q; h q[0];")
        .waitForResult(false)
        .build();
    IBMQConnectorResponseDto queuedResult =
        new IBMQConnectorResponseDto("job-123", STATUS_QUEUED, null, null);

    OutboundConnectorContext context = mock(OutboundConnectorContext.class);
    when(context.bindVariables(IBMQBaseRequest.class)).thenReturn(
        baseRequest(OperationMode.SUBMIT_JOB));
    when(context.bindVariables(IBMQSubmitJobRequestDto.class)).thenReturn(submitRequest);
    when(ibmqService.executeCircuit(submitRequest)).thenReturn(queuedResult);

    IBMQConnectorResponseDto result = (IBMQConnectorResponseDto) function.execute(context);

    assertThat(result.getStatus()).isEqualTo(STATUS_QUEUED);
    assertThat(result.getResult()).isNull();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private IBMQBaseRequest baseRequest(OperationMode mode) {
    return IBMQBaseRequest.builder().operationMode(mode).build();
  }

  private IBMQSubmitJobRequestDto buildOpenQasmRequest() {
    return IBMQSubmitJobRequestDto.builder()
        .apiKey("test-key")
        .ibmqUrl("https://quantum.cloud.ibm.com/api")
        .ibmqInstance("ibm-q/open/main")
        .backend("ibmq_qasm_simulator")
        .programId(PROGRAM_SAMPLER)
        .circuitInputMode(CircuitInputMode.OPEN_QASM)
        .circuit("OPENQASM 3.0; qubit[1] q; h q[0];")
        .shots(512)
        .waitForResult(true)
        .timeoutSeconds(30)
        .pollIntervalSeconds(5)
        .build();
  }

  private IBMQSubmitJobRequestDto buildDirectParamsRequest() {
    return IBMQSubmitJobRequestDto.builder()
        .apiKey("test-key")
        .ibmqUrl("https://quantum.cloud.ibm.com/api")
        .ibmqInstance("ibm-q/open/main")
        .backend("ibmq_qasm_simulator")
        .programId(PROGRAM_SAMPLER)
        .circuitInputMode(CircuitInputMode.DIRECT_PARAMS)
        .params("{\"pubs\": [[\"OPENQASM 3.0; qubit[1] q; h q[0];\", null, 1024]]}")
        .waitForResult(true)
        .timeoutSeconds(30)
        .pollIntervalSeconds(5)
        .build();
  }

  private IBMQGetJobResultRequestDto buildGetJobResultRequest() {
    return IBMQGetJobResultRequestDto.builder()
        .apiKey("test-key")
        .ibmqUrl("https://quantum.cloud.ibm.com/api")
        .ibmqInstance("ibm-q/open/main")
        .jobId("job-456")
        .build();
  }
}
