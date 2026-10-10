package com.pijamaparty.store.platform.application;

import java.util.UUID;

/**
 * Outbound port for new ids, so tests can control them (Dependency Inversion, like {@link java.time.Clock}).
 * Ids identify, they do not authorize: access without an account always goes through an {@code AccessToken}.
 */
@FunctionalInterface
public interface IdGenerator {

    UUID newId();
}
