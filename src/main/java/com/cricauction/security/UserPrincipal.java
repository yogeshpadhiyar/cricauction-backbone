package com.cricauction.security;

import com.cricauction.modules.auth.entity.User;
import com.cricauction.modules.auth.entity.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String fullName;
    private final String email;
    private final String mobile;
    private final String password;
    private final UserRole role;
    private final List<String> roles;
    private final boolean isVerified;
    private final boolean isActive;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(User user) {
        List<GrantedAuthority> authorities = (user.getRoles() != null && !user.getRoles().isEmpty())
                ? user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName().name()))
                .collect(Collectors.toList())
                : List.of(new SimpleGrantedAuthority("ROLE_PLAYER"));

        List<String> roleNames = user.getRoleNames();
        UserRole primaryRole = (user.getRoles() != null && !user.getRoles().isEmpty())
                ? user.getRoles().iterator().next().getName()
                : UserRole.PLAYER;

        return UserPrincipal.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .role(primaryRole)
                .roles(roleNames)
                .isVerified(user.isMobileVerified() || user.isEmailVerified())
                .isActive(user.isActive())
                .authorities(authorities)
                .build();
    }

    @Override
    public String getUsername() {
        return mobile != null ? mobile : email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isActive;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }
}
