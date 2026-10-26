package com.omni.common.web;

import com.omni.common.exception.BusinessException;

import java.util.Set;

/**
 * Identity of the caller, propagated by the API Gateway as trusted headers after JWT verification.
 * Services are never exposed publicly, so these headers can only come from the gateway.
 */
public record CurrentUser(Long id, String email, Set<String> roles) {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_STAFF = "STAFF";

    public boolean isAdmin() {
        return roles.contains(ROLE_ADMIN);
    }

    /** Back-office user: shop staff or administrator. */
    public boolean isStaff() {
        return isAdmin() || roles.contains(ROLE_STAFF);
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw BusinessException.forbidden();
        }
    }

    public void requireStaff() {
        if (!isStaff()) {
            throw BusinessException.forbidden();
        }
    }
}
