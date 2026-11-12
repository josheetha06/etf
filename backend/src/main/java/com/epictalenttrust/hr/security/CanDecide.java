package com.epictalenttrust.hr.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Hiring decisions (shortlist, move on, reject): HR admins, recruiters and hiring managers. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('" + Authorities.DECIDE + "')")
public @interface CanDecide {
}
