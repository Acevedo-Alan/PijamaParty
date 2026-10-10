package com.pijamaparty.store;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;

/**
 * Layer rules of ARCHITECTURE.md section 4 and 9. Module boundaries are verified by {@link ModularityTest}.
 *
 * <p>TODO: remove {@code allowEmptyShould(true)} from every rule once each layer package contains classes.
 * Since ArchUnit 1.0 a rule that matches no classes fails, and the skeleton packages are still empty.
 */
@AnalyzeClasses(packages = "com.pijamaparty.store", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_has_no_framework_dependencies =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "jakarta.persistence..", "com.fasterxml..", "..adapter..", "..application..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .allowEmptyShould(true);

    /** Application may use Spring stereotypes and {@code @Transactional}, nothing else from the framework. */
    @ArchTest
    static final ArchRule application_uses_only_stereotypes_and_transactions_from_frameworks =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat(
                resideInAPackage("org.springframework..")
                    .and(not(resideInAnyPackage("org.springframework.stereotype..",
                        "org.springframework.transaction.annotation..")))
                    .or(resideInAnyPackage("jakarta.persistence..", "jakarta.servlet..", "com.fasterxml..")))
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule no_field_injection =
        noFields().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .allowEmptyShould(true);

    /** Time comes from the injected {@link Clock}: {@code Instant.now(clock)} is allowed, {@code Instant.now()} is not. */
    @ArchTest
    static final ArchRule no_static_clock_usage =
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
            .should().callMethodWhere(readsTheSystemClock())
            .allowEmptyShould(true);

    private static DescribedPredicate<JavaMethodCall> readsTheSystemClock() {
        return DescribedPredicate.describe("read the system clock instead of the injected Clock", call -> {
            MethodCallTarget target = call.getTarget();
            String owner = target.getOwner().getName();
            String name = target.getName();
            boolean nowWithoutClock = owner.startsWith("java.time.") && name.equals("now")
                && !(target.getRawParameterTypes().size() == 1
                     && target.getRawParameterTypes().get(0).isEquivalentTo(Clock.class));
            boolean systemClock = owner.equals(Clock.class.getName()) && name.startsWith("system");
            boolean currentTimeMillis = owner.equals(System.class.getName()) && name.equals("currentTimeMillis");
            return nowWithoutClock || systemClock || currentTimeMillis;
        });
    }
}
