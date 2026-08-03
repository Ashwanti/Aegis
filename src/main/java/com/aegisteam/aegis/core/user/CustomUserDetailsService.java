package com.aegisteam.aegis.core.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads {@link UserDetails} from the database by email. Used both by the
 * login flow (indirectly, via {@link com.aegis.security.JwtAuthFilter}) and
 * on every authenticated request, since token validation re-fetches the user
 * rather than trusting stale claims — see playbook 1.3.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
        return new UserPrincipal(user);
    }
}
