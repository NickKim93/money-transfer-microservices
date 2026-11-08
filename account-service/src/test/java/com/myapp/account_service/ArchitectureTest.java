package com.myapp.account_service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.myapp.account_service",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule controllers_use_services =
            noClasses()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..repository..", "..model..", "..client..", "..messaging..");

    @ArchTest
    static final ArchRule services_do_not_depend_on_web =
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..controller..", "..web..");

    @ArchTest
    static final ArchRule models_are_independent_of_application_layers =
            noClasses()
                    .that()
                    .resideInAPackage("..model..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..controller..",
                            "..web..",
                            "..service..",
                            "..repository..",
                            "..dto..",
                            "..messaging..",
                            "..client..");

    @ArchTest
    static final ArchRule repositories_do_not_depend_on_transport =
            noClasses()
                    .that()
                    .resideInAPackage("..repository..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..controller..", "..web..", "..dto..", "..messaging..", "..client..");
}
