package com.pijamaparty.store.accounts.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.application.PasswordHasherContractTest;
import org.junit.jupiter.api.Test;

class BCryptPasswordHasherContractTest extends PasswordHasherContractTest<BCryptPasswordHasher> {

    @Override
    protected BCryptPasswordHasher createSubject() {
        return new BCryptPasswordHasher();
    }

    @Test
    void givenAPassword_whenHashed_thenItIsBCryptWithCost12AndTheAlgorithmPrefix() {
        assertThat(subject.hash("correct horse battery").value())
            .matches("^\\{bcrypt}\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$");
    }

    @Test
    void givenTheSamePasswordTwice_whenHashed_thenTheHashesDifferBecauseOfTheSalt() {
        assertThat(subject.hash("correct horse battery")).isNotEqualTo(subject.hash("correct horse battery"));
    }
}
