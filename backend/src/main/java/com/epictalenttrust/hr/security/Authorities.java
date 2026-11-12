package com.epictalenttrust.hr.security;

import com.epictalenttrust.hr.hruser.HrUserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;

public final class Authorities {

    /** Run the hiring work: jobs, CVs, scheduling, offers. HR admins and recruiters. */
    public static final String MANAGE_HIRING = "MANAGE_HIRING";
    /** Make hiring decisions. HR admins, recruiters and hiring managers. */
    public static final String DECIDE = "DECIDE";
    /** Use the HR portal at all. Every role except the panel login. */
    public static final String HR_ACCESS = "HR_ACCESS";

    private Authorities() {
    }

    public static List<GrantedAuthority> forRole(HrUserRole role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        if (role.canManageHiring()) {
            authorities.add(new SimpleGrantedAuthority(MANAGE_HIRING));
        }
        if (role.canDecide()) {
            authorities.add(new SimpleGrantedAuthority(DECIDE));
        }
        if (role != HrUserRole.PANEL) {
            authorities.add(new SimpleGrantedAuthority(HR_ACCESS));
        }
        return authorities;
    }
}
