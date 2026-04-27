package de.envite.connector.ibmq;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@AnalyzeClasses(
    packages = "de.envite.connector.ibmq",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

  // -------------------------------------------------------------------------
  // 1. Layer dependency rules
  // -------------------------------------------------------------------------

  @ArchTest
  static final ArchRule connector_must_not_bypass_service_to_authenticator =
      noClasses().that().haveSimpleName("IBMQConnectorFunction")
          .should().dependOnClassesThat().haveSimpleName("IBMQAuthenticator")
          .because(
              "IBMQConnectorFunction must delegate to IBMQService, not call other classes directly");

  @ArchTest
  static final ArchRule connector_must_not_bypass_service_to_job_client =
      noClasses().that().haveSimpleName("IBMQConnectorFunction")
          .should().dependOnClassesThat().haveSimpleName("IBMQJobClient")
          .because(
              "IBMQConnectorFunction must delegate to IBMQService, not call other classes directly");

  @ArchTest
  static final ArchRule connector_must_not_bypass_service_to_parameter_handler =
      noClasses().that().haveSimpleName("IBMQConnectorFunction")
          .should().dependOnClassesThat().haveSimpleName("IBMQParameterHandler")
          .because(
              "IBMQConnectorFunction must delegate to IBMQService, not call other classes directly");

  @ArchTest
  static final ArchRule dtos_must_not_depend_on_service_or_infrastructure =
      noClasses().that().resideInAPackage("..dto..")
          .should().dependOnClassesThat().haveSimpleName("IBMQService")
          .orShould().dependOnClassesThat().haveSimpleName("IBMQAuthenticator")
          .orShould().dependOnClassesThat().haveSimpleName("IBMQJobClient")
          .orShould().dependOnClassesThat().haveSimpleName("IBMQParameterHandler")
          .because(
              "DTOs are plain data objects and must not depend on service or infrastructure classes");

  @ArchTest
  static final ArchRule util_must_not_depend_on_domain_classes =
      noClasses().that().resideInAPackage("..util..")
          .should().dependOnClassesThat().resideInAPackage("de.envite.connector.ibmq")
          .because("Utility classes must remain domain-agnostic");

  // -------------------------------------------------------------------------
  // 2. Naming convention rules
  // -------------------------------------------------------------------------

  // IBMQBaseRequest is excluded: it is a lightweight peeking class used before the full DTO is bound,
  // not a standard DTO, and therefore exempt from the *Dto suffix requirement.
  @ArchTest
  static final ArchRule concrete_dto_classes_must_end_with_dto =
      classes().that().resideInAPackage("..dto..")
          .and().areTopLevelClasses()
          .and().doNotHaveSimpleName("IBMQBaseRequest")
          .should().haveSimpleNameEndingWith("Dto")
          .because("Concrete top-level classes in the dto package must end with 'Dto'");

  @ArchTest
  static final ArchRule dto_classes_must_start_with_ibmq =
      classes().that().resideInAPackage("..dto..")
          .and().areTopLevelClasses()
          .should().haveSimpleNameStartingWith("IBMQ")
          .because("All top-level classes in the dto package must be prefixed with 'IBMQ'");

  @ArchTest
  static final ArchRule constants_classes_must_have_only_private_constructors =
      classes().that().haveSimpleNameEndingWith("Constants")
          .should().haveOnlyPrivateConstructors()
          .because("Constants classes are utility holders and must not be instantiated");

  // -------------------------------------------------------------------------
  // 3. Spring annotation rules
  // -------------------------------------------------------------------------

  @ArchTest
  static final ArchRule service_classes_must_be_annotated_with_service =
      classes().that().haveSimpleNameEndingWith("Service")
          .should().beAnnotatedWith(Service.class)
          .because("Classes ending with 'Service' must be annotated with @Service");

  @ArchTest
  static final ArchRule dtos_must_not_be_spring_beans =
      noClasses().that().resideInAPackage("..dto..")
          .should().beAnnotatedWith(Service.class)
          .orShould().beAnnotatedWith(Component.class)
          .because("DTOs are plain data objects and must not be registered as Spring beans");

  @ArchTest
  static final ArchRule model_classes_must_not_be_spring_beans =
      noClasses().that().resideInAPackage("..model..")
          .should().beAnnotatedWith(Service.class)
          .orShould().beAnnotatedWith(Component.class)
          .because("Domain model classes must not be registered as Spring beans");

  // -------------------------------------------------------------------------
  // 4. Camunda connector API rules
  // -------------------------------------------------------------------------

  @ArchTest
  static final ArchRule only_connector_function_implements_outbound_connector_function =
      classes().that().implement(OutboundConnectorFunction.class)
          .should().haveSimpleNameEndingWith("ConnectorFunction")
          .because(
              "OutboundConnectorFunction must only be implemented by classes named '*ConnectorFunction'");

  @ArchTest
  static final ArchRule outbound_connector_annotation_requires_implementation =
      classes().that().areAnnotatedWith(OutboundConnector.class)
          .should().implement(OutboundConnectorFunction.class)
          .because(
              "@OutboundConnector must only be placed on OutboundConnectorFunction implementations");

  // -------------------------------------------------------------------------
  // 5. No cyclic dependencies between sub-packages
  // -------------------------------------------------------------------------

  @ArchTest
  static final ArchRule no_cycles_between_subpackages =
      slices().matching("de.envite.connector.ibmq.(*)..")
          .should().beFreeOfCycles();
}
