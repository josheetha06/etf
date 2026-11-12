package com.epictalenttrust.hr.security;

import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.company.Company;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.hruser.HrUser;
import com.epictalenttrust.hr.hruser.HrUserRepository;
import com.epictalenttrust.hr.hruser.HrUserStatus;
import com.epictalenttrust.hr.security.LoginLogWriter.Event;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Two-step sign-in: work email and password, then a 6-digit code from an authenticator app.
 * On first sign-in the user scans a new authenticator key before the account is unlocked.
 */
@Service
public class AuthService {

    static final String PENDING_USER = "ett.signin.userId";
    static final String PENDING_SECRET = "ett.signin.newTotpSecret";
    static final String PENDING_SINCE = "ett.signin.startedAt";
    private static final Duration PENDING_LIFETIME = Duration.ofMinutes(10);
    private static final String BAD_CREDENTIALS = "Email or password is incorrect.";

    private final HrUserRepository users;
    private final CompanyRepository companies;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totp;
    private final SecretCipher cipher;
    private final LoginLogWriter loginLog;
    private final SecurityContextRepository contextRepository;
    private final EttProperties properties;
    private final Clock clock;

    public AuthService(HrUserRepository users, CompanyRepository companies, PasswordEncoder passwordEncoder,
                       TotpService totp, SecretCipher cipher, LoginLogWriter loginLog,
                       SecurityContextRepository contextRepository, EttProperties properties, Clock clock) {
        this.users = users;
        this.companies = companies;
        this.passwordEncoder = passwordEncoder;
        this.totp = totp;
        this.cipher = cipher;
        this.loginLog = loginLog;
        this.contextRepository = contextRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public record PasswordStepResult(String nextStep, String otpauthUri, String secret) {
    }

    /**
     * Checks the password. HR logins then need their authenticator code. The shared panel login is signed in straight
     * away: it can only open a review with an employee ID and the interview code emailed to that panel member.
     */
    @Transactional
    public PasswordStepResult checkPassword(String email, String password, HttpServletRequest request,
                                            HttpServletResponse response) {
        HrUser user = users.findByEmail(email.trim()).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(password, "{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z0kH1HhWJrvCz2kL4yGx3t6e");
            loginLog.record(null, email, Event.FAILED_PASSWORD, false, "unknown_email", request);
            throw unauthorized(BAD_CREDENTIALS);
        }
        ensureNotLocked(user, request);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginLog.record(user.getId(), email, Event.FAILED_PASSWORD, false, "wrong_password", request);
            throw unauthorized(BAD_CREDENTIALS);
        }
        ensureAllowed(user, request);

        if (user.getRole() == com.epictalenttrust.hr.hruser.HrUserRole.PANEL) {
            request.getSession(true);
            signIn(user, request, response);
            return new PasswordStepResult("done", null, null);
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(PENDING_USER, user.getId());
        session.setAttribute(PENDING_SINCE, clock.instant());
        session.removeAttribute(PENDING_SECRET);
        if (user.getTotpSecret() == null) {
            String secret = totp.newSecret();
            session.setAttribute(PENDING_SECRET, secret);
            String uri = totp.otpauthUri(properties.security().totpIssuer(), user.getWorkEmail(), secret);
            return new PasswordStepResult("totp_setup", uri, secret);
        }
        return new PasswordStepResult("totp", null, null);
    }

    @Transactional
    public HrUserPrincipal checkCode(String code, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        UUID userId = session == null ? null : (UUID) session.getAttribute(PENDING_USER);
        Instant since = session == null ? null : (Instant) session.getAttribute(PENDING_SINCE);
        if (userId == null || since == null || since.plus(PENDING_LIFETIME).isBefore(clock.instant())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "sign_in_expired", "Your sign-in timed out. Please enter your password again.");
        }
        HrUser user = users.findById(userId).orElseThrow(() -> unauthorized(BAD_CREDENTIALS));
        ensureNotLocked(user, request);
        ensureAllowed(user, request);

        String newSecret = (String) session.getAttribute(PENDING_SECRET);
        String secret = newSecret != null ? newSecret : cipher.decrypt(user.getTotpSecret());
        if (!totp.verify(secret, code == null ? null : code.trim())) {
            loginLog.record(user.getId(), user.getWorkEmail(), Event.FAILED_TOTP, false, "wrong_code", request);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "wrong_code", "That code didn't work. Check your authenticator app and try again.");
        }
        if (newSecret != null) {
            user.setTotpSecret(cipher.encrypt(newSecret));
        }
        session.removeAttribute(PENDING_USER);
        session.removeAttribute(PENDING_SECRET);
        session.removeAttribute(PENDING_SINCE);
        return signIn(user, request, response);
    }

    private HrUserPrincipal signIn(HrUser user, HttpServletRequest request, HttpServletResponse response) {
        user.recordSignIn(clock.instant());
        request.changeSessionId();

        HrUserPrincipal principal = new HrUserPrincipal(user.getId(), user.getCompanyId(), user.getFullName(),
                user.getWorkEmail(), user.getRole());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                Authorities.forRole(user.getRole())));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        loginLog.record(user.getId(), user.getWorkEmail(), Event.SIGN_IN, true, null, request);
        return principal;
    }

    /**
     * Sets a new password. After a one-time password the current one isn't asked for again (it was just used to
     * sign in); otherwise it must be given.
     */
    @Transactional
    public void changePassword(String current, String next, HttpServletRequest request) {
        HrUser user = users.findById(CurrentUser.id()).orElseThrow(() -> unauthorized(BAD_CREDENTIALS));
        if (!user.isMustChangePassword() && (current == null || !passwordEncoder.matches(current, user.getPasswordHash()))) {
            loginLog.record(user.getId(), user.getWorkEmail(), Event.FAILED_PASSWORD, false, "wrong_current_password", request);
            throw ApiException.badRequest("wrong_password", "Your current password isn't right.");
        }
        String password = next == null ? "" : next;
        if (password.length() < 12) {
            throw ApiException.badRequest("weak_password", "Use at least 12 characters.");
        }
        if (password.toLowerCase(java.util.Locale.ROOT).contains(user.getWorkEmail().split("@")[0].toLowerCase(java.util.Locale.ROOT))) {
            throw ApiException.badRequest("weak_password", "Don't use your email address in your password.");
        }
        if (passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("same_password", "Choose a password you haven't used here before.");
        }
        user.changePassword(passwordEncoder.encode(password), clock.instant());
        loginLog.record(user.getId(), user.getWorkEmail(), Event.PASSWORD_RESET, true, "changed_by_user", request);
    }

    public void signOut(HttpServletRequest request) {
        CurrentUser.find().ifPresent(user ->
                loginLog.record(user.id(), user.email(), Event.SIGN_OUT, true, null, request));
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private void ensureNotLocked(HrUser user, HttpServletRequest request) {
        int lockoutMinutes = properties.security().lockoutMinutes();
        Instant windowStart = clock.instant().minus(Duration.ofMinutes(lockoutMinutes));
        if (loginLog.recentFailures(user.getId(), windowStart) >= properties.security().maxFailedSignIns()) {
            loginLog.record(user.getId(), user.getWorkEmail(), Event.LOCKED, false, "too_many_attempts", request);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "locked",
                    "Too many attempts. Please wait " + lockoutMinutes + " minutes and try again.");
        }
    }

    private void ensureAllowed(HrUser user, HttpServletRequest request) {
        if (user.getStatus() == HrUserStatus.DISABLED) {
            loginLog.record(user.getId(), user.getWorkEmail(), Event.SIGN_IN, false, "user_disabled", request);
            throw ApiException.forbidden("user_disabled", "Your account is turned off. Ask your HR admin to turn it back on.");
        }
        Company company = companies.findById(user.getCompanyId()).orElseThrow();
        if (!company.canUsePortal()) {
            loginLog.record(user.getId(), user.getWorkEmail(), Event.SIGN_IN, false, "company_" + company.getStatus().name().toLowerCase(), request);
            throw ApiException.forbidden("company_paused", "Your company's access to the portal is paused. Please contact Epic Talent Trust.");
        }
    }

    private static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "bad_credentials", message);
    }
}
