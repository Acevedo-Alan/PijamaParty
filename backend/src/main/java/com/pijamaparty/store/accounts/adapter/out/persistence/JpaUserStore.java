package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.application.LoadUser;
import com.pijamaparty.store.accounts.application.SaveUser;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Runs inside the caller's transaction, so the caller's actor decides which rows RLS shows (RNF-27). */
@Repository
class JpaUserStore implements LoadUser, SaveUser {

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
    public void save(User user) {
        users.save(UserEntity.from(user));
    }
}
