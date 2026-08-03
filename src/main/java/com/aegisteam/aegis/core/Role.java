package com.aegisteam.aegis.core;

/**
 * Application roles. Spring Security authorities are derived from this as
 * {@code ROLE_<name>} — see {@link com.aegis.core.user.UserPrincipal}.
 * AUDITOR is enforced read-only at the method-security layer in Phase 4.4;
 * this enum only defines the identity, not the enforcement.
 */
public enum Role {
    ADMIN,
    ANALYST,
    AUDITOR
}
