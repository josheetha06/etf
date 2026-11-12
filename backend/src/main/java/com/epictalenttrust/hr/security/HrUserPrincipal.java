package com.epictalenttrust.hr.security;

import com.epictalenttrust.hr.hruser.HrUserRole;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/** The signed-in HR user, kept in the HTTP session. */
public record HrUserPrincipal(UUID id, UUID companyId, String fullName, String email, HrUserRole role)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
