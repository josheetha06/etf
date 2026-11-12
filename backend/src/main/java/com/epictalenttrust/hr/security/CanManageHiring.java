package com.epictalenttrust.hr.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Changes to jobs, shortlists, interview times and decisions. Interviewers are read-only. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('" + Authorities.MANAGE_HIRING + "')")
public @interface CanManageHiring {
}
