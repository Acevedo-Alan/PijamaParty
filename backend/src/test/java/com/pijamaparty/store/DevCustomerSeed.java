package com.pijamaparty.store;

import com.pijamaparty.store.accounts.api.EmailVerificationLinks;
import com.pijamaparty.store.accounts.application.RegisterCustomer;
import com.pijamaparty.store.accounts.application.VerifyCustomerEmail;
import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * T-E8-29: a customer to sign in with on {@code ./mvnw spring-boot:test-run@dev}, whose throwaway database starts
 * empty. It goes through the store's own flow, so every rule applies: it registers (UC-19), mints the verification
 * link the email would carry and opens it, so the account is verified as a customer's would be; outside this seed the
 * dev profile sends no email (no Resend key). On a database that already has the account, nothing changes.
 * {@code -Dstore.dev.demo-customer=false} turns it off. Test sources only: never part of the jar, and the password
 * below exists only on a developer's machine.
 */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "store.dev.demo-customer", havingValue = "true", matchIfMissing = true)
class DevCustomerSeed {

    private static final Logger log = LoggerFactory.getLogger(DevCustomerSeed.class);

    /** On example.com, a domain that never receives mail (RFC 2606). */
    static final String EMAIL = "cliente.prueba@example.com";
    static final String PASSWORD = "PijamaPrueba2026";
    static final String NAME = "Cliente Prueba";

    /** Who calls the store's API without signing in: the registration and the verification link are public. */
    private static final Actor VISITOR = Actor.anonymous(Optional.empty(), Optional.empty());

    @Bean
    ApplicationRunner devCustomerSeedRunner(RegisterCustomer registerCustomer, EmailVerificationLinks links,
                                            VerifyCustomerEmail verifyCustomerEmail) {
        return new Runner(registerCustomer, links, verifyCustomerEmail);
    }

    private record Runner(RegisterCustomer registerCustomer, EmailVerificationLinks links,
                          VerifyCustomerEmail verifyCustomerEmail) implements ApplicationRunner {

        @Override
        public void run(ApplicationArguments args) {
            var registered = CurrentActor.callAs(VISITOR,
                () -> registerCustomer.execute(new RegisterCustomer.Command(EMAIL, PASSWORD, NAME)));
            if (registered instanceof RegisterCustomer.Result.EmailAlreadyRegistered) {
                log.info("Demo customer skipped: the database already has it");
                return;
            }
            if (!(registered instanceof RegisterCustomer.Result.Registered(var customer))) {
                throw new IllegalStateException("The demo customer could not register: " + registered);
            }
            // As the email would: the link is minted as SYSTEM, in the transaction of issue().
            var link = CurrentActor.callAsSystem(() -> links.issue(customer.value()))
                .orElseThrow(() -> new IllegalStateException("No verification link for the demo customer"));
            var verified = CurrentActor.callAs(VISITOR,
                () -> verifyCustomerEmail.execute(new VerifyCustomerEmail.Command(link.token().value())));
            if (!(verified instanceof VerifyCustomerEmail.Result.Verified)) {
                throw new IllegalStateException("The demo customer's email could not be verified: " + verified);
            }
            log.info("Demo customer ready: sign in with the email and password of DevCustomerSeed");
        }
    }
}
