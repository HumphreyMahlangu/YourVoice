package io.github.humphreymahlangu.votetrust.security;

import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    private UserPrincipal(UserAccount userAccount) {
        this.id = userAccount.getId();
        this.email = userAccount.getEmail();
        this.passwordHash = userAccount.getPasswordHash();
        this.enabled = userAccount.isEnabled();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + userAccount.getRole().name()));
    }

    public static UserPrincipal from(UserAccount userAccount) {
        return new UserPrincipal(userAccount);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String role() {
        return authorities.getFirst().getAuthority().replaceFirst("^ROLE_", "");
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
    public boolean isEnabled() {
        return enabled;
    }
}
