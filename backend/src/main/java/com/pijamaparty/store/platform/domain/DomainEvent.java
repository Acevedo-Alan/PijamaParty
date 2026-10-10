package com.pijamaparty.store.platform.domain;

/**
 * Marks a domain event published to other modules. Events are stored serialized in a table without Row Level
 * Security, so they carry ids and non-personal values only, never personal data or access tokens
 * (ARCHITECTURE.md section 6, RNF-27). {@code ArchitectureTest} enforces it.
 */
public interface DomainEvent {
}
