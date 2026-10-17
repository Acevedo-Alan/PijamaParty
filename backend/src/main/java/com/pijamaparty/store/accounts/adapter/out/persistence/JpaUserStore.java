package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.FindUserByEmail;
import com.pijamaparty.store.accounts.application.LoadUser;
import com.pijamaparty.store.accounts.application.SaveUser;
import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Runs inside the caller's transaction, so the caller's actor decides which rows RLS shows (RNF-27). */
@Repository
class JpaUserStore implements LoadUser, SaveUser, FindUserByEmail {

    private final UserJpaRepository users;

    JpaUserStore(UserJpaRepository users) {
        this.users = users;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return users.findById(id.value()).map(UserEntity::toDomain);
    }

    @Override
    public boolean administratorExists() {
        return users.existsByRole(UserRole.ADMIN);
    }

    @Override
    public Optional<User> findAdministrator() {
        return users.findByRole(UserRole.ADMIN).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return users.findOneByEmail(email.value()).map(UserEntity::toDomain);
    }

    @Override
    public void save(User user) {
        users.save(UserEntity.from(user));
    }
}
