package com.pijamaparty.store.accounts.adapter.in.startup;

import com.pijamaparty.store.accounts.application.ResetAdministratorSecondFactor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * T-E1-16: with {@code ADMIN_RESET_SECOND_FACTOR=true}, deletes the administrator's second factor and recovery codes
 * at startup, as {@code SYSTEM}, for an administrator who lost both the phone and the codes. The next sign-in enrolls
 * again; the variable must be removed afterwards (runbook, T-E6-08).
 */
@Component
class SecondFactorResetRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecondFactorResetRunner.class);

    private final ResetAdministratorSecondFactor reset;
    private final boolean requested;

    SecondFactorResetRunner(ResetAdministratorSecondFactor reset,
                            @Value("${store.admin.reset-second-factor:false}") boolean requested) {
        this.reset = reset;
        this.requested = requested;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!requested) {
            return;
        }
        switch (CurrentActor.callAsSystem(reset::execute)) {
            case ResetAdministratorSecondFactor.Result.Reset done -> log.warn(
                "The administrator's second factor was reset (ADMIN_RESET_SECOND_FACTOR); the next sign-in enrolls "
                    + "again. Remove the variable now.");
            case ResetAdministratorSecondFactor.Result.NothingToReset nothing -> log.warn(
                "ADMIN_RESET_SECOND_FACTOR is set but there is no second factor to reset. Remove the variable.");
        }
    }
}
