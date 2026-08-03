    package com.aegisteam.aegis.core.user;

    import java.util.Collection;
    import java.util.List;
    import java.util.UUID;
    import org.springframework.security.core.GrantedAuthority;
    import org.springframework.security.core.authority.SimpleGrantedAuthority;
    import org.springframework.security.core.userdetails.UserDetails;

    /**
     * Adapts {@link User} to Spring Security's {@link UserDetails}. This is what
     * ends up in the {@code SecurityContextHolder} and is what
     * {@code @CurrentUser} resolves to in controller methods.
     */
    public class UserPrincipal implements UserDetails {

        private final UUID id;
        private final UUID orgId;
        private final UUID entityId;
        private final String email;
        private final String passwordHash;
        private final String fullName;
        private final boolean enabled;
        private final List<GrantedAuthority> authorities;

        public UserPrincipal(User user) {
            this.id = user.getId();
            this.orgId = user.getOrgId();
            this.entityId = user.getEntityId();
            this.email = user.getEmail();
            this.passwordHash = user.getPasswordHash();
            this.fullName = user.getFullName();
            this.enabled = user.isEnabled();
            this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        }

        public UUID getId() {
            return id;
        }

        public UUID getOrgId() {
            return orgId;
        }

        public UUID getEntityId() {
            return entityId;
        }

        public String getFullName() {
            return fullName;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public String getPassword() {
            return passwordHash;
        }

        @Override
        public String getUsername() {
            return email;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
