package com.resolveiq.backend.security;

import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TenantAuthenticationToken extends AbstractAuthenticationToken {

    private final TenantContext tenantContext;

    public TenantAuthenticationToken(TenantContext tenantContext) {
        super(buildAuthorities(tenantContext.role()));
        this.tenantContext = tenantContext;
        setAuthenticated(true);
    }

    private static Collection<? extends GrantedAuthority> buildAuthorities(Role role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return tenantContext;
    }

    public TenantContext getTenantContext() {
        return tenantContext;
    }
}
