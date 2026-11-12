package com.epictalenttrust.hr.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Same policy as frontend/nginx/security-headers.conf. */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/totp").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // The partner assessor's one-time report link and the client's logo on the sign-in page.
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Signed in as anyone: who am I, change password, sign out.
                        .requestMatchers("/api/auth/**").authenticated()
                        // The generic panel login sees only the panel pages; HR roles see everything else.
                        .requestMatchers("/api/panel/**").hasRole("PANEL")
                        .requestMatchers("/api/**").hasAuthority(com.epictalenttrust.hr.security.Authorities.HR_ACCESS)
                        // The web app's own files, when it is served from here (portable edition).
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> writeProblem(response,
                                HttpServletResponse.SC_UNAUTHORIZED, "unauthenticated", "Please sign in."))
                        .accessDeniedHandler((request, response, ex) -> {
                            if (ex instanceof CsrfException) {
                                writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "csrf",
                                        "Your session expired. Please refresh the page.");
                            } else {
                                writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "forbidden",
                                        "Your role can't do this.");
                            }
                        }))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    public static void writeProblem(HttpServletResponse response, int status, String code, String detail)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
    }
}
