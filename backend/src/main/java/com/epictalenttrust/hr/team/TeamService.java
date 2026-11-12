package com.epictalenttrust.hr.team;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.hruser.HrUser;
import com.epictalenttrust.hr.hruser.HrUserRepository;
import com.epictalenttrust.hr.hruser.HrUserRole;
import com.epictalenttrust.hr.hruser.HrUserStatus;
import com.epictalenttrust.hr.mail.MailGateway;
import com.epictalenttrust.hr.security.CurrentUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * An HR admin's view of their team: who can sign in, with which role. New people get a one-time password, shown
 * once to the admin, and choose their own password and set up two-step sign-in the first time they sign in.
 */
@Service
public class TeamService {

    public record Member(UUID id, String fullName, String email, HrUserRole role, HrUserStatus status,
                         boolean twoStepSetUp, boolean mustChangePassword, Instant lastSignInAt, Instant addedAt,
                         boolean me) {
    }

    public record NewMember(@NotBlank @Size(max = 120) String fullName,
                            @NotBlank @Email @Size(max = 200) String email,
                            @NotNull HrUserRole role) {
    }

    /** The one-time password is returned only here, once. */
    public record Created(UUID id, String fullName, String email, HrUserRole role, String oneTimePassword) {
    }

    public record Change(HrUserRole role, Boolean active, @Size(max = 120) String fullName) {
    }

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);
    private static final int MAX_AT_ONCE = 25;

    private final HrUserRepository users;
    private final CompanyRepository companies;
    private final PasswordEncoder passwordEncoder;
    private final ActivityService activity;
    private final MailGateway mail;
    private final EttProperties properties;
    private final JdbcClient jdbc;
    private final Clock clock;

    public TeamService(HrUserRepository users, CompanyRepository companies, PasswordEncoder passwordEncoder,
                       ActivityService activity, MailGateway mail, EttProperties properties, JdbcClient jdbc,
                       Clock clock) {
        this.users = users;
        this.companies = companies;
        this.passwordEncoder = passwordEncoder;
        this.activity = activity;
        this.mail = mail;
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Member> list() {
        UUID me = CurrentUser.id();
        return users.findByCompanyIdOrderByFullName(CurrentUser.companyId()).stream()
                .map(u -> toMember(u, me))
                .toList();
    }

    /** Adds one or more people. Either all are added or none (for example if one email is already in use). */
    @Transactional
    public List<Created> add(List<NewMember> people) {
        if (people == null || people.isEmpty()) {
            throw ApiException.badRequest("no_people", "Add at least one person.");
        }
        if (people.size() > MAX_AT_ONCE) {
            throw ApiException.badRequest("too_many", "Add up to " + MAX_AT_ONCE + " people at a time.");
        }
        Set<String> seen = new HashSet<>();
        for (NewMember person : people) {
            if (person.role() == HrUserRole.INTERVIEWER) {
                throw ApiException.badRequest("role_retired",
                        "The Interviewer role is no longer used. Interview panels use the panel login instead.");
            }
            String email = person.email().strip().toLowerCase(Locale.ROOT);
            if (!seen.add(email)) {
                throw ApiException.badRequest("duplicate_email", email + " is in the list twice.");
            }
            if (users.findByEmail(email).isPresent()) {
                throw ApiException.conflict("email_in_use", email + " already has a login.");
            }
        }
        UUID companyId = CurrentUser.companyId();
        String company = companies.findById(companyId).map(c -> c.getName()).orElse("your company");
        String admin = CurrentUser.get().fullName();
        List<Created> created = new ArrayList<>();
        for (NewMember person : people) {
            String password = OneTimePasswords.generate();
            HrUser user = new HrUser(companyId, person.fullName().strip(), person.email().strip().toLowerCase(Locale.ROOT),
                    person.role());
            user.setOneTimePassword(passwordEncoder.encode(password));
            user = users.save(user);
            activity.record("team.member_added", "hr_user", user.getId());
            created.add(new Created(user.getId(), user.getFullName(), user.getWorkEmail(), user.getRole(), password));
            invite(user, company, admin);
        }
        return created;
    }

    @Transactional
    public Member change(UUID userId, Change change) {
        HrUser user = member(userId);
        boolean self = user.getId().equals(CurrentUser.id());
        if (change.fullName() != null && !change.fullName().isBlank()) {
            user.setFullName(change.fullName().strip());
        }
        if (change.role() == HrUserRole.INTERVIEWER) {
            throw ApiException.badRequest("role_retired",
                    "The Interviewer role is no longer used. Interview panels use the panel login instead.");
        }
        if (change.role() != null && change.role() != user.getRole()) {
            if (self && change.role() != HrUserRole.ADMIN) {
                throw ApiException.badRequest("own_role", "You can't change your own role. Ask another HR admin.");
            }
            if (user.getRole() == HrUserRole.ADMIN) {
                requireAnotherAdmin(user);
            }
            user.setRole(change.role());
            activity.record("team.role_changed", "hr_user", user.getId());
        }
        if (change.active() != null) {
            boolean active = user.getStatus() != HrUserStatus.DISABLED;
            if (!change.active() && active) {
                if (self) {
                    throw ApiException.badRequest("own_access", "You can't turn off your own access.");
                }
                if (user.getRole() == HrUserRole.ADMIN) {
                    requireAnotherAdmin(user);
                }
                user.setStatus(HrUserStatus.DISABLED);
                activity.record("team.access_off", "hr_user", user.getId());
            } else if (change.active() && !active) {
                user.setStatus(user.getLastSignInAt() == null ? HrUserStatus.INVITED : HrUserStatus.ACTIVE);
                activity.record("team.access_on", "hr_user", user.getId());
            }
        }
        return toMember(user, CurrentUser.id());
    }

    /** A new one-time password, for someone who has forgotten theirs. */
    @Transactional
    public Created resetPassword(UUID userId) {
        HrUser user = member(userId);
        if (user.getId().equals(CurrentUser.id())) {
            throw ApiException.badRequest("own_password", "Change your own password from the menu under your name.");
        }
        String password = OneTimePasswords.generate();
        user.setOneTimePassword(passwordEncoder.encode(password));
        clearFailedSignIns(user);
        activity.record("team.password_reset", "hr_user", user.getId());
        return new Created(user.getId(), user.getFullName(), user.getWorkEmail(), user.getRole(), password);
    }

    /** For a lost phone: the person scans a new QR code the next time they sign in. */
    @Transactional
    public Member resetTwoStep(UUID userId) {
        HrUser user = member(userId);
        user.setTotpSecret(null);
        activity.record("team.two_step_reset", "hr_user", user.getId());
        return toMember(user, CurrentUser.id());
    }

    private HrUser member(UUID userId) {
        HrUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Person"));
        if (!user.getCompanyId().equals(CurrentUser.companyId())) {
            throw ApiException.notFound("Person");
        }
        return user;
    }

    private void requireAnotherAdmin(HrUser user) {
        long admins = users.findByCompanyIdOrderByFullName(user.getCompanyId()).stream()
                .filter(u -> u.getRole() == HrUserRole.ADMIN && u.getStatus() != HrUserStatus.DISABLED)
                .filter(u -> !u.getId().equals(user.getId()))
                .count();
        if (admins == 0) {
            throw ApiException.conflict("last_admin", "Your company needs at least one HR admin. Make someone else an admin first.");
        }
    }

    private void clearFailedSignIns(HrUser user) {
        // Lockout counts failed attempts since the last successful sign-in or password reset.
        jdbc.sql("""
                insert into login_log (user_kind, user_id, email_attempted, event, success, failure_reason)
                values ('hr_user', :u, :e, 'password_reset', true, 'reset_by_admin')
                """)
                .param("u", user.getId()).param("e", user.getWorkEmail()).update();
    }

    private void invite(HrUser user, String company, String admin) {
        String link = properties.portalUrl() + "/login";
        String text = """
                Hello %s,

                %s has given you access to the %s hiring portal.

                Sign in at %s with this email address. %s will give you a one-time password.
                The first time you sign in you'll choose your own password and set up two-step sign-in with an
                authenticator app on your phone.

                The ETT team""".formatted(user.getFullName().split("\\s+")[0], admin, company, link, admin.split("\\s+")[0]);
        String subject = "Your login for the " + company + " hiring portal";
        String to = user.getWorkEmail();
        Runnable send = () -> {
            String event = "sent";
            String detail = null;
            try {
                mail.send(to, subject, text, null);
            } catch (Exception ex) {
                log.warn("Invitation email to {} failed: {}", to, ex.getMessage());
                event = "failed";
                detail = ex.getMessage();
            }
            jdbc.sql("insert into email_log (to_address, provider, event, detail, occurred_at) values (:to, :p, cast(:e as email_event_t), :d, :at)")
                    .param("to", to).param("p", mail.providerName()).param("e", event).param("d", detail)
                    .param("at", Timestamp.from(clock.instant())).update();
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private static Member toMember(HrUser u, UUID me) {
        return new Member(u.getId(), u.getFullName(), u.getWorkEmail(), u.getRole(), u.getStatus(),
                u.getTotpSecret() != null, u.isMustChangePassword(), u.getLastSignInAt(), u.getCreatedAt(),
                u.getId().equals(me));
    }
}
