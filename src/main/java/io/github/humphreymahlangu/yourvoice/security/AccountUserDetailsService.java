package io.github.humphreymahlangu.votetrust.security;

import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import io.github.humphreymahlangu.votetrust.repository.UserAccountRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public AccountUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String normalizedEmail = username.trim().toLowerCase(Locale.ROOT);
        UserAccount userAccount = userAccountRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User account not found"));
        return UserPrincipal.from(userAccount);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(UUID id) {
        UserAccount userAccount = userAccountRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User account not found"));
        return UserPrincipal.from(userAccount);
    }
}
