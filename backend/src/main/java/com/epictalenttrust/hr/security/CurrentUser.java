package com.epictalenttrust.hr.security;

import com.epictalenttrust.hr.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<HrUserPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HrUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static HrUserPrincipal get() {
        return find().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Please sign in."));
    }

    public static UUID id() {
        return get().id();
    }

    public static UUID companyId() {
        return get().companyId();
    }

    public static UUID idOrNull() {
        return find().map(HrUserPrincipal::id).orElse(null);
    }
}
