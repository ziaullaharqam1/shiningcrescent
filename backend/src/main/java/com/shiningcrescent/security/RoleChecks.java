package com.shiningcrescent.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class RoleChecks {
    private RoleChecks() {}

    public static boolean supplierPortal(Authentication auth) {
        if (auth == null) {
            return false;
        }
        boolean supplier = has(auth, "ROLE_SUPPLIER");
        if (!supplier) {
            return false;
        }
        return !(has(auth, "ROLE_SUPER_ADMIN")
                || has(auth, "ROLE_TRADING_MANAGER")
                || has(auth, "ROLE_PROCUREMENT_OFFICER")
                || has(auth, "ROLE_QUALITY_INSPECTOR")
                || has(auth, "ROLE_WAREHOUSE_KEEPER"));
    }

    public static boolean has(Authentication auth, String authority) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }

    public static boolean hasAny(Authentication auth, String... authorities) {
        for (String a : authorities) {
            if (has(auth, a)) {
                return true;
            }
        }
        return false;
    }
}
