package com.uaiou.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Encoda as "regras de camada, verificáveis" do RF-01.2 como testes reais. Passam hoje vazias —
 * nenhuma das quatro regras exige que um módulo exista, só que, quando existir, respeite o desenho:
 * controller → service → repository, DTO na borda, entidade nunca exposta, transação só no service.
 * Assim que T-03 criar {@code com.uaiou.auth.AuthController}, a regra passa a valer sobre ele
 * automaticamente.
 */
class LayeringArchitectureTest {

  private static JavaClasses appClasses;

  @BeforeAll
  static void importClasses() {
    appClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.uaiou");
  }

  @Test
  void controllersDoNotAccessRepositoriesDirectly() {
    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .allowEmptyShould(true);

    rule.check(appClasses);
  }

  @Test
  void repositoriesDoNotKnowApiDtos() {
    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Repository")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..dto..")
            .allowEmptyShould(true);

    rule.check(appClasses);
  }

  @Test
  void controllersNeverSerializeJpaEntitiesDirectly() {
    ArchRule rule =
        noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .dependOnClassesThat()
            .areAnnotatedWith(Entity.class)
            .allowEmptyShould(true);

    rule.check(appClasses);
  }

  @Test
  void transactionalBoundaryLivesOnlyInTheServiceLayer() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .beAnnotatedWith(Transactional.class)
            .allowEmptyShould(true);

    rule.check(appClasses);
  }
}
