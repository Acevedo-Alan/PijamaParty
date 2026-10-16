package com.pijamaparty.store;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.pijamaparty.store.platform.domain.AccessToken;
import com.pijamaparty.store.platform.domain.DomainEvent;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

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

    /**
     * Running as SYSTEM turns RLS into "see everything": only background adapters (webhooks, jobs) and the platform
     * may do it, never business rules, use cases or customer-facing controllers (RNF-27).
     */
    @ArchTest
    static final ArchRule system_role_only_in_background_adapters =
        noClasses().that().resideInAnyPackage("..domain..", "..application..", "..adapter.in.web..")
            .should().callMethodWhere(DescribedPredicate.describe("run as SYSTEM", call ->
                call.getTarget().getOwner().isEquivalentTo(CurrentActor.class)
                    && call.getTarget().getName().matches("(call|run)AsSystem.*")))
            .allowEmptyShould(true);

    /** Events are stored serialized without RLS: ids only (ARCHITECTURE.md section 6, RNF-27). */
    @ArchTest
    static final ArchRule domain_events_carry_no_personal_data =
        noFields().that().areDeclaredInClassesThat().implement(DomainEvent.class)
            .should(holdPersonalData())
            .allowEmptyShould(true);

    /**
     * springdoc names an OpenAPI schema after the simple class name and silently keeps only one of two records with
     * the same name, so the generated frontend client would type one endpoint with the other's body.
     */
    @ArchTest
    static final ArchRule web_records_have_unique_names =
        classes().that().resideInAPackage("..adapter.in.web..").and().areRecords()
            .should(haveAUniqueSimpleName())
            .allowEmptyShould(true);

    private static ArchCondition<JavaClass> haveAUniqueSimpleName() {
        return new ArchCondition<>("have a unique simple name, which becomes their OpenAPI schema name") {
            private Map<String, Long> counts = Map.of();

            @Override
            public void init(Collection<JavaClass> records) {
                counts = records.stream().map(JavaClass::getSimpleName)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            }

            @Override
            public void check(JavaClass record, ConditionEvents events) {
                boolean unique = counts.getOrDefault(record.getSimpleName(), 0L) == 1;
                events.add(new SimpleConditionEvent(record, unique,
                    record.getName() + " has the simple name of another web record"));
            }
        };
    }

    private static final Pattern PERSONAL_FIELD_NAME =
        Pattern.compile("(?i).*(email|nationalid|dni|address|phone|name|token|password).*");
    private static final Set<String> PERSONAL_TYPES = Set.of("Email", "NationalId", "Address", "Phone");

    private static ArchCondition<JavaField> holdPersonalData() {
        return new ArchCondition<>("hold personal data or access tokens") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                boolean personal = PERSONAL_FIELD_NAME.matcher(field.getName()).matches()
                    || PERSONAL_TYPES.contains(field.getRawType().getSimpleName())
                    || field.getRawType().isAssignableTo(AccessToken.class);
                events.add(new SimpleConditionEvent(field, personal,
                    field.getFullName() + " looks like personal data or a token"));
            }
        };
    }

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
