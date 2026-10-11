package com.pijamaparty.store.accounts.adapter.out.persistence;

import com.pijamaparty.store.accounts.domain.Email;
import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.accounts.domain.PasswordHash;
import com.pijamaparty.store.accounts.domain.SecondFactor;
import com.pijamaparty.store.accounts.domain.User;
import com.pijamaparty.store.accounts.domain.UserRole;
import com.pijamaparty.store.platform.domain.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Row of {@code app_user} (V5, V11). Translated to and from the {@link User} aggregate; never leaves this package. */
@Entity
@Table(name = "app_user")
class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "totp_secret")
    private byte[] totpSecret;

    @Column(name = "totp_enabled_at")
    private Instant totpEnabledAt;

    @Column(name = "totp_last_step")
    private Long totpLastStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    static UserEntity from(User user) {
        var entity = new UserEntity();
        entity.id = user.id().value();
        entity.email = user.email().value();
        entity.emailVerified = user.emailVerified();
        entity.passwordHash = user.passwordHash().map(PasswordHash::value).orElse(null);
        entity.name = user.name();
        entity.role = user.role();
        entity.createdAt = user.createdAt();
        entity.copySignInStateFrom(user);
        return entity;
    }

    /** The only state the sign-in may change (the database enforces the same for SYSTEM, V5 and V11). */
    void copySignInStateFrom(User user) {
        failedLoginAttempts = user.failedSignInAttempts();
        lockedUntil = user.lockedUntil().orElse(null);
        Optional<SecondFactor> secondFactor = user.secondFactor();
        totpSecret = secondFactor.map(factor -> factor.secret().bytes()).orElse(null);
        totpEnabledAt = secondFactor.map(SecondFactor::enabledAt).orElse(null);
        totpLastStep = secondFactor.map(SecondFactor::lastStep).orElse(null);
        updatedAt = user.updatedAt();
    }

    User toDomain() {
        return User.restore(UserId.of(id), new Email(email), emailVerified,
            passwordHash == null ? null : new PasswordHash(passwordHash), name, role, failedLoginAttempts, lockedUntil,
            totpSecret == null ? null
                : new SecondFactor(new EncryptedSecret(totpSecret), totpEnabledAt, totpLastStep),
            createdAt, updatedAt);
    }
}
