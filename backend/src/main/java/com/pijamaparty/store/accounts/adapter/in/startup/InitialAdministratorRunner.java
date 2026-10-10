package com.pijamaparty.store.accounts.adapter.in.startup;

import com.pijamaparty.store.accounts.application.CreateInitialAdministrator;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * UC-04: creates the first administrator at startup from {@code ADMIN_EMAIL}, {@code ADMIN_NAME} and the
 * {@code ADMIN_INITIAL_PASSWORD} secret, as {@code SYSTEM} (RLS lets only the system create it). Once an
 * administrator exists these values are ignored. Logs never include the email or the password (R-05).
 */
@Component
class InitialAdministratorRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdministratorRunner.class);

    private final CreateInitialAdministrator createInitialAdministrator;
    private final String email;
    private final String name;
    private final String password;

    InitialAdministratorRunner(CreateInitialAdministrator createInitialAdministrator,
                               @Value("${store.admin.email:}") String email,
                               @Value("${store.admin.name:}") String name,
                               @Value("${store.admin.initial-password:}") String password) {
        this.createInitialAdministrator = createInitialAdministrator;
        this.email = email;
        this.name = name;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && name.isBlank() && password.isBlank()) {
            return;
        }
        var command = new CreateInitialAdministrator.Command(email, name, password);
        switch (CurrentActor.callAsSystem(() -> createInitialAdministrator.execute(command))) {
            case CreateInitialAdministrator.Result.Created created ->
                log.info("Initial administrator created (user id {})", created.id());
            case CreateInitialAdministrator.Result.AlreadyExists exists ->
                log.debug("An administrator already exists; the initial administrator settings are ignored");
            case CreateInitialAdministrator.Result.InvalidEmail invalid ->
                log.error("Initial administrator not created: ADMIN_EMAIL is not a valid email");
            case CreateInitialAdministrator.Result.InvalidName invalid ->
                log.error("Initial administrator not created: ADMIN_NAME must have 1 to 100 characters");
            case CreateInitialAdministrator.Result.InvalidPassword invalid ->
                log.error("Initial administrator not created: ADMIN_INITIAL_PASSWORD is {} (12 characters to 72 bytes)",
                    invalid.violation());
        }
    }
}
