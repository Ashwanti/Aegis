package com.aegisteam.aegis.security;



import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method parameter to be resolved to the authenticated
 * {@link com.aegis.core.user.UserPrincipal} from the security context.
 *
 * <pre>{@code
 * @PostMapping("/exceptions/{id}/notes")
 * public NoteResponse addNote(@PathVariable UUID id, @CurrentUser UserPrincipal user, ...) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
