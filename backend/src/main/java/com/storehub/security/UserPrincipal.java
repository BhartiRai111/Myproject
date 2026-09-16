package com.storehub.security;

import com.storehub.entity.User;
import com.storehub.service.RolePermissions;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    /**
     * The existing {@code ROLE_<name>} authority (unchanged — every pre-Step-5
     * {@code @PreAuthorize("hasRole(...)")}/{@code hasAnyRole(...)} check keeps working
     * exactly as before) plus a {@code PERM_<name>} authority per {@link RolePermissions}
     * entry for the user's role. Rebuilt fresh on every request (see
     * {@code CustomUserDetailsService}, called per-request by {@code JwtAuthenticationFilter}
     * rather than trusting a role/permission claim baked into the JWT itself), so an ADMIN
     * changing a user's role takes effect on that user's very next request — never a stale
     * authorization claim in an old-but-still-valid token.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        RolePermissions.forRole(user.getRole())
                .forEach(permission -> authorities.add(new SimpleGrantedAuthority("PERM_" + permission.name())));
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
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
        return user.getStatus() == com.storehub.entity.UserStatus.ACTIVE;
    }
}
