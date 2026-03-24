package de.envite.connector.ibmq.deployment;

import io.camunda.client.CamundaClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
@Component
@ConditionalOnProperty(name = "ibmq.example.deploy", havingValue = "true")
public class IBMQExampleWorkflowDeployer implements ApplicationRunner {

    private final CamundaClient camundaClient;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[IBMQExampleWorkflowDeployer] Deploying example workflow and forms");
        camundaClient.newDeployResourceCommand()
                .addResourceFromClasspath("example/ibmq-input-form.form")
                .addResourceFromClasspath("example/ibmq-result-form.form")
                .addResourceFromClasspath("example/ibmq-example-workflow_blocking.bpmn")
                .addResourceFromClasspath("example/ibmq-example-workflow_polling.bpmn")
                .send()
                .join();
        log.info("[IBMQExampleWorkflowDeployer] Example workflow deployed successfully");
    }
}
