package com.pijamaparty.store.platform.application;

import java.util.UUID;

/** Outbound port for new ids, so tests can control them (Dependency Inversion, like {@link java.time.Clock}). */
@FunctionalInterface
public interface IdGenerator {

    UUID newId();
}
