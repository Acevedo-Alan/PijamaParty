package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.domain.UserRole;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    /** {@code SELECT ... FOR UPDATE}: concurrent sign-in attempts on one account are counted one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserEntity> findByEmail(String email);

    /** Without a lock: the first read of a sign-in, before the password check. */
    Optional<UserEntity> findOneByEmail(String email);

    /** As {@link #findByEmail}, by id: the steps after the password (T-E1-16). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserEntity> findLockedById(UUID id);

    boolean existsByRole(UserRole role);

    Optional<UserEntity> findByRole(UserRole role);
}
