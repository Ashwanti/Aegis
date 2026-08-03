package com.aegisteam.aegis.api.auth.dto;

import com.aegisteam.aegis.core.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String fullName,
        @NotBlank String organizationName,
        @NotNull Role role) {

    /**
     * {@code role} is required rather than defaulted. Boot disables Jackson's
     * FAIL_ON_UNKNOWN_PROPERTIES, so a misspelled key ("Role", "user_role") is
     * dropped and arrives null — a default here would turn every such mistake
     * into a silent ANALYST instead of a 400.
     */
    public RegisterRequest {
        organizationName = organizationName == null ? null : organizationName.trim();
    }
}
