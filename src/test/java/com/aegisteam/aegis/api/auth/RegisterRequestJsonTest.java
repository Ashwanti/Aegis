package com.aegisteam.aegis.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisteam.aegis.api.auth.dto.RegisterRequest;
import com.aegisteam.aegis.core.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Binding-level cover for the {@code role} field. Boot 4 uses Jackson 3
 * ({@code tools.jackson}) for request conversion even though Jackson 2 is still
 * on the classpath via jjwt — these assert against the one that actually runs.
 */
class RegisterRequestJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void bindsExplicitRole() {
        RegisterRequest request = mapper.readValue(
                """
                {"email":"a@b.com","password":"secret123","fullName":"T",
                 "organizationName":"Acme","role":"ADMIN"}
                """,
                RegisterRequest.class);

        assertEquals(Role.ADMIN, request.role());
    }

    @Test
    void bindsAuditorRole() {
        RegisterRequest request = mapper.readValue(
                """
                {"email":"a@b.com","password":"secret123","fullName":"T",
                 "organizationName":"","role":""}
                """,
                RegisterRequest.class);

        assertEquals(Role.AUDITOR, request.role());
    }

    /**
     * Regression cover for the "role is stuck on ANALYST" bug. Boot disables
     * FAIL_ON_UNKNOWN_PROPERTIES, so a misspelled key is dropped and role
     * arrives null. It must stay null here — @NotNull then turns it into a 400.
     * Defaulting it instead made every such typo look like a hardcoded ANALYST.
     */
    @Test
    void misspelledRoleKeyLeavesRoleNullRatherThanDefaulting() {
        JsonMapper bootStyle = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        RegisterRequest request = bootStyle.readValue(
                """
                {"email":"a@b.com","password":"secret123","fullName":"T",
                 "organizationName":"Acme","Role":"ADMIN"}
                """,
                RegisterRequest.class);

        assertNull(request.role());
    }

    @Test
    void absentRoleLeavesRoleNull() {
        RegisterRequest request = mapper.readValue(
                """
                {"email":"a@b.com","password":"secret123","fullName":"T",
                 "organizationName":"Acme"}
                """,
                RegisterRequest.class);

        assertNull(request.role());
    }

    @Test
    @Timeout(10)
    void unknownRoleIsReportedWithTheValidValues() {
        InvalidFormatException cause = assertThrows(
                InvalidFormatException.class,
                () -> mapper.readValue(
                        """
                        {"email":"a@b.com","password":"secret123","fullName":"T",
                         "organizationName":"Acme","role":"SUPERUSER"}
                        """,
                        RegisterRequest.class));

        ResponseEntity<Map<String, Object>> response = new AuthExceptionHandler()
                .handleUnreadableBody(new HttpMessageNotReadableException("malformed", cause, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                "role must be one of [ADMIN, ANALYST, AUDITOR]",
                response.getBody().get("message"));
    }
}
