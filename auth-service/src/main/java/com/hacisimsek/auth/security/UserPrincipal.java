package com.hacisimsek.auth.security;

import com.hacisimsek.auth.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final boolean emailVerified;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal from(User user) {
        var authorities = List.of(new SimpleGrantedAuthority(user.getRole().name()));
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash() != null ? user.getPasswordHash() : "",
                user.isEmailVerified(),
                authorities
        );
    }

    @Override public String getUsername() {
        return email;
    }
    @Override public boolean isAccountNonExpired() {
        return true;
    }
    @Override public boolean isAccountNonLocked() {
        return true;
    }
    @Override public boolean isCredentialsNonExpired() {
        return true;
    }
    @Override public boolean isEnabled() {
        return emailVerified;
    }
}
