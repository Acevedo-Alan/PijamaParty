package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.SignInMethods;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;

/**
 * Outbound port: a customer's sign-in methods (UC-27, T-E4-15) within the caller's transaction, as that customer: Row
 * Level Security shows it only its own account and external accounts (V5, V34).
 */
public interface LoadSignInMethods {

    /** @return empty when the account does not exist */
    Optional<SignInMethods> findByCustomer(UserId customer);

    /**
     * For a change: first locks the customer's account row until the transaction ends ({@code SELECT ... FOR
     * UPDATE}), so two changes of its sign-in methods run one after the other and never leave it without a way in.
     *
     * @return empty when the account does not exist
     */
    Optional<SignInMethods> lockByCustomer(UserId customer);
}
