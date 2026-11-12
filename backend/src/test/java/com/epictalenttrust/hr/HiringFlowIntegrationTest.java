package com.epictalenttrust.hr;

import com.epictalenttrust.hr.security.TotpService;
import com.epictalenttrust.hr.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks one recruiter through the whole flow over HTTP: sign-in, a job with its own rounds, CV, shortlist, an AI
 * interview, a partner assessment, a panel interview reviewed through the panel login, the offer, leave cover and the
 * audit trail.
 */
class HiringFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42!";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TotpService totp;
    @Autowired com.epictalenttrust.hr.company.CompanyDefaults companyDefaults;

    private String run;
    private String recruiterEmail;
    private String otherCompanyEmail;
    private String colleagueEmail;
    private String panelEmail;
    private UUID recruiterId;
    private UUID colleagueId;

    @BeforeEach
    void seedTwoCompanies() {
        run = UUID.randomUUID().toString().substring(0, 8);
        recruiterEmail = "recruiter-" + run + "@acme.example";
        otherCompanyEmail = "admin-" + run + "@globex.example";
        colleagueEmail = "colleague-" + run + "@acme.example";
        panelEmail = "panel-" + run + "@acme.example";
        UUID acme = seedCompany("Acme " + run, "India", "apac");
        companyDefaults.apply(acme);
        recruiterId = seedUser(acme, recruiterEmail, "recruiter");
        colleagueId = seedUser(acme, colleagueEmail, "recruiter");
        seedUser(acme, panelEmail, "panel");
        seedUser(seedCompany("Globex " + run, "United Kingdom", "uk"), otherCompanyEmail, "admin");
    }

    @Test
    void recruiterRunsTheWholeFlow() throws Exception {
        // Unauthenticated calls are refused, and changes need the CSRF token.
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", recruiterEmail, "password", PASSWORD))))
                .andExpect(status().isForbidden());

        MockHttpSession recruiter = signIn(recruiterEmail);
        mvc.perform(get("/api/auth/me").session(recruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("recruiter"))
                .andExpect(jsonPath("$.canManageHiring").value(true))
                .andExpect(jsonPath("$.seesAllJobs").value(false));

        // A job with three different rounds, in the order HR chose: AI interview, partner assessor, panel.
        String jobId = createJob(recruiter, "Cloud Engineer " + run, true, List.of(
                round("AI interview", "AI_INTERVIEW", "ONLINE", 45),
                round("Partner assessment", "PARTNER", "IN_PERSON", 60),
                round("Technical Level 1", "PANEL", "ONLINE", 90)));
        mvc.perform(get("/api/jobs/{id}", jobId).session(recruiter))
                .andExpect(jsonPath("$.rounds.length()").value(3))
                .andExpect(jsonPath("$.rounds[2].name").value("Technical Level 1"))
                .andExpect(jsonPath("$.rounds[2].durationMin").value(90))
                .andExpect(jsonPath("$.owner.id").value(recruiterId.toString()));

        // Upload a CV: it is read and scored in the background within seconds.
        String applicationId = uploadCv(recruiter, jobId);
        mvc.perform(get("/api/applications/{id}", applicationId).session(recruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screening.evidence[0].matched").value(true))
                .andExpect(jsonPath("$.screening.evidence[0].strength").value("RECENT_ROLE"))
                .andExpect(jsonPath("$.screening.summary").isNotEmpty())
                .andExpect(jsonPath("$.nextStep.action").value("decide_shortlist"));

        // Shortlist, then Round 1: the AI interview, here completed with a sample report.
        postJson(recruiter, "/api/applications/" + applicationId + "/shortlist-decision", Map.of("decision", "SHORTLISTED"));
        mvc.perform(get("/api/applications/{id}", applicationId).session(recruiter))
                .andExpect(jsonPath("$.nextStep.action").value("schedule_round"))
                .andExpect(jsonPath("$.nextStep.round").value(1))
                .andExpect(jsonPath("$.nextStep.roundKind").value("AI_INTERVIEW"));
        String start = java.time.Instant.now().plus(java.time.Duration.ofDays(3)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString();
        String r1 = postJson(recruiter, "/api/applications/" + applicationId + "/schedule/ai-interview",
                Map.of("startAt", start, "candidateZone", "Asia/Kolkata"));
        assertThat((String) JsonPath.read(r1, "$.status")).isEqualTo("INVITED");
        mvc.perform(post("/api/interviews/{id}/sample-report", (String) JsonPath.read(r1, "$.id")).session(recruiter).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/applications/{id}", applicationId).session(recruiter))
                .andExpect(jsonPath("$.nextStep.action").value("decide_round"))
                .andExpect(jsonPath("$.rounds[0].aiReport.sample").value(true))
                .andExpect(jsonPath("$.rounds[0].aiReport.identityCheck").isNotEmpty())
                // The AI interviewer confirms where the candidate is; the CV already said Chennai.
                .andExpect(jsonPath("$.rounds[0].aiReport.candidateLocation").value("Chennai"))
                .andExpect(jsonPath("$.location.source").value("cv"))
                .andExpect(jsonPath("$.location.status").value("known"));
        mvc.perform(get("/api/candidates").param("q", "Arjun").param("jobId", jobId).session(recruiter))
                .andExpect(jsonPath("$.items[0].location.label").value("Chennai"))
                .andExpect(jsonPath("$.items[0].location.source").value("cv"));
        mvc.perform(get("/api/reports").param("view", "to_review").session(recruiter))
                .andExpect(jsonPath("$[?(@.type == 'ai_interview')].candidateName").value("Arjun Nair"));
        decide(recruiter, applicationId, "ROUND1");

        // Round 2: a seat at a partner assessment centre with an assessor, who reports through a one-time link.
        // Arjun is in Chennai, so only the Chennai centre is offered; Madurai is an exception that needs a reason.
        UUID centre = jdbc.queryForObject("insert into partner_centres (name, city) values (?, 'Chennai') returning id",
                UUID.class, "Centre " + run);
        jdbc.update("insert into partner_assessors (centre_id, full_name) values (?, 'Asha Assessor')", centre);
        jdbc.update("insert into centre_slots (centre_id, start_at, end_at, seats) values (?, now() + interval '4 days', now() + interval '4 days 1 hour', 1)", centre);
        UUID madurai = jdbc.queryForObject("insert into partner_centres (name, city) values (?, 'Madurai') returning id",
                UUID.class, "Madurai centre " + run);
        jdbc.update("insert into partner_assessors (centre_id, full_name) values (?, 'Mani Assessor')", madurai);
        jdbc.update("insert into centre_slots (centre_id, start_at, end_at, seats) values (?, now() + interval '2 days', now() + interval '2 days 1 hour', 1)", madurai);
        String centres = mvc.perform(get("/api/applications/{id}/partner-centres", applicationId).session(recruiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.found").value(true))
                .andExpect(jsonPath("$.nearestCity").value("Chennai"))
                .andReturn().getResponse().getContentAsString();
        List<String> slotIds = JsonPath.read(centres, "$.centres[?(@.name == 'Centre " + run + "')].slots[0].id");
        List<String> listed = JsonPath.read(centres, "$.centres[*].city");
        assertThat(listed).containsOnly("Chennai");
        String withOthers = mvc.perform(get("/api/applications/{id}/partner-centres", applicationId)
                        .param("otherCities", "true").session(recruiter))
                .andReturn().getResponse().getContentAsString();
        List<String> maduraiSlots = JsonPath.read(withOthers, "$.centres[?(@.name == 'Madurai centre " + run + "')].slots[0].id");
        List<Boolean> maduraiNearest = JsonPath.read(withOthers, "$.centres[?(@.name == 'Madurai centre " + run + "')].inNearestCity");
        assertThat(maduraiNearest).containsExactly(false);
        mvc.perform(post("/api/applications/{id}/schedule/partner", applicationId).session(recruiter).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("slotId", maduraiSlots.getFirst()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("outside_reason_required"));
        String r2 = postJson(recruiter, "/api/applications/" + applicationId + "/schedule/partner", Map.of("slotId", slotIds.getFirst()));
        assertThat((String) JsonPath.read(r2, "$.centreName")).isEqualTo("Centre " + run);
        // The booking moves the centre to the back of the round robin queue.
        assertThat(jdbc.queryForObject("select last_turn from partner_centres where id = ?", Long.class, centre)).isNotNull();
        assertThat(jdbc.queryForObject("select booked from centre_slots where centre_id = ?", Integer.class, centre)).isEqualTo(1);
        mvc.perform(post("/api/interviews/{id}/sample-report", (String) JsonPath.read(r2, "$.id")).session(recruiter).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/applications/{id}", applicationId).session(recruiter))
                .andExpect(jsonPath("$.nextStep.action").value("decide_round"))
                .andExpect(jsonPath("$.rounds[1].partnerReport.areaScores.length()").value(5));
        decide(recruiter, applicationId, "ROUND2");

        // HR can correct where the candidate is with a town or PIN code; a place that can't be found is refused.
        mvc.perform(put("/api/applications/{id}/location", applicationId).session(recruiter).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("text", "Nowhere Town"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("location_not_found"));
        mvc.perform(put("/api/applications/{id}/location", applicationId).session(recruiter).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("text", "Sivakasi 626123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Sivakasi, Virudhunagar, Tamil Nadu 626123"))
                .andExpect(jsonPath("$.source").value("hr"))
                .andExpect(jsonPath("$.nearestCity").value("Madurai"));

        // Round 3: a panel interview. HR types in the panel; the member gets an interview code.
        String r3 = postJson(recruiter, "/api/applications/" + applicationId + "/schedule/panel", Map.of(
                "startAt", start, "candidateZone", "Asia/Kolkata", "durationMin", 90, "mode", "ONLINE",
                "meetingLink", "https://meet.example.com/abc",
                "panel", List.of(Map.of("employeeId", "EMP-9001", "fullName", "Priya Panel", "email", "priya-" + run + "@acme.example"))));
        String interviewId = JsonPath.read(r3, "$.id");
        assertThat((Integer) JsonPath.read(r3, "$.panelSize")).isEqualTo(1);
        String detail = mvc.perform(get("/api/applications/{id}", applicationId).session(recruiter))
                .andReturn().getResponse().getContentAsString();
        List<String> codes = JsonPath.read(detail, "$.interviews[?(@.round == 3)].panelCode");
        String code = codes.getFirst();
        assertThat(code).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
        // Let the interview have happened, so the review form is open.
        jdbc.update("update interviews set start_at = now() - interval '2 hours' where id = ?::uuid", interviewId);

        // The shared panel login signs in without an authenticator, and only sees the panel pages.
        MockHttpSession panel = new MockHttpSession();
        passwordStep(panel, panelEmail, PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.nextStep").value("done"));
        mvc.perform(get("/api/jobs").session(panel)).andExpect(status().isForbidden());
        mvc.perform(post("/api/panel/access").session(panel).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", "EMP-9001", "code", "AAAA-BBBB"))))
                .andExpect(status().isBadRequest());
        String review = postJson(panel, "/api/panel/access", Map.of("employeeId", "emp-9001", "code", code.toLowerCase()));
        assertThat((String) JsonPath.read(review, "$.candidateName")).isEqualTo("Arjun Nair");
        List<String> skills = JsonPath.read(review, "$.skills[*].name");
        List<String> measures = JsonPath.read(review, "$.measures");
        assertThat(skills).contains("AWS", "Terraform");
        assertThat(measures).isNotEmpty();
        Map<String, Object> answers = Map.of(
                "skills", skills.stream().map(n -> Map.of("name", n, "score", 4)).toList(),
                "measures", measures.stream().map(n -> Map.of("name", n, "score", 3)).toList(),
                "strengths", "Clear on AWS networking and Terraform modules.",
                "concerns", "Less hands-on with monitoring tools.",
                "summary", "A solid engineer who explained trade-offs well and would fit the platform team.",
                "recommendation", "hire");
        // Sending without every score is refused; a complete review is accepted.
        mvc.perform(post("/api/panel/review/submit").session(panel).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("skills", List.of(), "measures", List.of(), "recommendation", "hire"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("scores_missing"));
        String sent = postJson(panel, "/api/panel/review/submit", answers);
        assertThat((String) JsonPath.read(sent, "$.submittedAt")).isNotBlank();

        mvc.perform(get("/api/applications/{id}", applicationId).session(recruiter))
                .andExpect(jsonPath("$.nextStep.action").value("decide_round"))
                .andExpect(jsonPath("$.rounds[2].panelReport.submitted").value(1))
                .andExpect(jsonPath("$.rounds[2].panelReport.combined").value("ADVANCE"))
                .andExpect(jsonPath("$.rounds[2].panelReport.reviews[0].employeeId").value("EMP-9001"));
        decide(recruiter, applicationId, "ROUND3");
        mvc.perform(get("/api/applications/{id}", applicationId).session(recruiter))
                .andExpect(jsonPath("$.stage").value("OFFER"))
                .andExpect(jsonPath("$.nextStep.action").value("record_offer"));
        postJson(recruiter, "/api/applications/" + applicationId + "/offer-outcome", Map.of("accepted", true));
        mvc.perform(get("/api/applications/{id}", applicationId).session(recruiter))
                .andExpect(jsonPath("$.status").value("HIRED"));
        mvc.perform(get("/api/notifications/count").session(recruiter))
                .andExpect(jsonPath("$.unread").value(org.hamcrest.Matchers.greaterThan(0)));

        // On hold needs a reason; reopening brings the job back.
        mvc.perform(post("/api/jobs/{id}/status", jobId).session(recruiter).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ON_HOLD"))))
                .andExpect(status().isBadRequest());
        postJson(recruiter, "/api/jobs/" + jobId + "/status", Map.of("status", "ON_HOLD", "reason", "Budget review this month"));
        mvc.perform(get("/api/jobs/{id}", jobId).session(recruiter))
                .andExpect(jsonPath("$.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.statusReason").value("Budget review this month"));
        postJson(recruiter, "/api/jobs/" + jobId + "/status", Map.of("status", "OPEN"));

        // Only admins change the logo; the assessor link needs a real token.
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/settings/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3}))
                        .session(recruiter).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/public/assessor/{token}", "not-a-token")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/candidate/{token}", "not-a-token")).andExpect(status().isNotFound());

        // A colleague sees the job only while covering for the recruiter.
        MockHttpSession colleague = signIn(colleagueEmail);
        mvc.perform(get("/api/jobs/{id}", jobId).session(colleague)).andExpect(status().isNotFound());
        postJson(recruiter, "/api/leave-cover", Map.of("coverUserId", colleagueId.toString(),
                "startsOn", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString(),
                "endsOn", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(5).toString()));
        mvc.perform(get("/api/jobs/{id}", jobId).session(colleague))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coveringFor").value("Test recruiter"));

        // Another company cannot see any of it.
        MockHttpSession outsider = signIn(otherCompanyEmail);
        mvc.perform(get("/api/jobs/{id}", jobId).session(outsider)).andExpect(status().isNotFound());
        mvc.perform(get("/api/applications/{id}/cv", applicationId).session(outsider)).andExpect(status().isNotFound());
        mvc.perform(get("/api/applications/{id}", applicationId).session(outsider)).andExpect(status().isNotFound());
        mvc.perform(get("/api/reports").param("view", "all").session(outsider))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/audit").param("jobId", jobId).session(outsider))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));

        // The audit trail names the person behind each change, and what changed.
        List<Map<String, Object>> trail = jdbc.queryForList("""
                select table_name, operation, changed_by, actor_kind::text as actor_kind
                from audit_trail where record_id = ?::uuid order by id""", jobId);
        assertThat(trail).isNotEmpty().allSatisfy(row -> {
            assertThat(row.get("changed_by")).isEqualTo(recruiterId);
            assertThat(row.get("actor_kind")).isEqualTo("hr_user");
        });
        assertThat(trail).extracting(row -> row.get("operation")).contains("INSERT", "UPDATE");
        String audit = mvc.perform(get("/api/audit").param("jobId", jobId).param("type", "job").session(recruiter))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> who = JsonPath.read(audit, "$.items[*].who");
        assertThat(who).contains("Test recruiter");
        List<String> reviewers = JsonPath.read(mvc.perform(get("/api/audit").param("interviewId", interviewId).session(recruiter))
                .andReturn().getResponse().getContentAsString(), "$.items[*].who");
        assertThat(reviewers).anyMatch(w -> w.contains("Priya Panel"));
    }

    @Test
    void withoutAiShortlistingCandidatesGoStraightToRoundOne() throws Exception {
        MockHttpSession recruiter = signIn(recruiterEmail);
        String jobId = createJob(recruiter, "Data Engineer " + run, false, List.of(
                round("Technical Level 1", "PANEL", "IN_PERSON", 60),
                round("AI interview", "AI_INTERVIEW", "ONLINE", 45)));
        String applicationId = uploadCv(recruiter, jobId);
        mvc.perform(get("/api/applications/{id}", applicationId).session(recruiter))
                .andExpect(jsonPath("$.stage").value("ROUND1"))
                .andExpect(jsonPath("$.nextStep.action").value("schedule_round"))
                .andExpect(jsonPath("$.nextStep.roundKind").value("PANEL"));
        mvc.perform(get("/api/applications/{id}/current-round", applicationId).session(recruiter))
                .andExpect(jsonPath("$.name").value("Technical Level 1"))
                .andExpect(jsonPath("$.mode").value("IN_PERSON"));
        // A round candidates have reached keeps its type.
        mvc.perform(put("/api/jobs/{id}", jobId).session(recruiter).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(jobBody("Data Engineer " + run, false, List.of(
                                round("Technical Level 1", "AI_INTERVIEW", "ONLINE", 45)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("round_in_use"));
    }

    @Test
    void lockoutAfterRepeatedWrongPasswords() throws Exception {
        for (int i = 0; i < 5; i++) {
            passwordStep(new MockHttpSession(), recruiterEmail, "wrong-password").andExpect(status().isUnauthorized());
        }
        passwordStep(new MockHttpSession(), recruiterEmail, PASSWORD).andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("locked"));
    }

    /**
     * The everyday tools: personal stars, ready-made reasons, the Overview's join link, a no-show with a new time,
     * undoing a decision (the email is held back), and closing a job (interviews cancelled, candidates told).
     */
    @Test
    void everydayTools() throws Exception {
        MockHttpSession recruiter = signIn(recruiterEmail);
        String jobId = createJob(recruiter, "Platform Engineer " + run, false, List.of(
                round("Technical Level 1", "PANEL", "ONLINE", 60)));

        // Stars are personal: they put the job in the Starred tab, first.
        mvc.perform(put("/api/jobs/{id}/star", jobId).session(recruiter).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.starred").value(true));
        mvc.perform(get("/api/jobs").param("status", "starred").session(recruiter))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(jobId))
                .andExpect(jsonPath("$[0].starred").value(true));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/jobs/{id}/star", jobId)
                        .session(recruiter).with(csrf()))
                .andExpect(jsonPath("$.starred").value(false));
        mvc.perform(get("/api/jobs/{id}", jobId).session(recruiter)).andExpect(jsonPath("$.starred").value(false));

        // Ready-made reasons for "Not progressing" come with the company.
        mvc.perform(get("/api/settings/decision-reasons").session(recruiter))
                .andExpect(jsonPath("$.length()").value(6));

        // AI shortlisting is off, so the candidate goes straight to Technical Level 1.
        String applicationId = uploadCv(recruiter, jobId);
        String booked = bookPanel(recruiter, applicationId);
        String interviewId = JsonPath.read(booked, "$.id");

        // Today, online, with the recruiter on the panel: the Overview offers the join link.
        jdbc.update("update interviews set start_at = now() + interval '1 minute' where id = ?::uuid", interviewId);
        mvc.perform(get("/api/dashboard").session(recruiter))
                .andExpect(jsonPath("$.needsAttention").exists())
                .andExpect(jsonPath("$.toBook").exists())
                .andExpect(jsonPath("$.messages.unread").value(0))
                .andExpect(jsonPath("$.today[0].onPanel").value(true))
                .andExpect(jsonPath("$.today[0].meetingLink").value("https://meet.example.com/abc-defg"));

        // The candidate didn't attend: record it and offer a new time. The invitation says they missed the last one.
        jdbc.update("update interviews set start_at = now() - interval '1 hour' where id = ?::uuid", interviewId);
        mvc.perform(post("/api/interviews/{id}/no-show", interviewId).session(recruiter).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));
        bookPanel(recruiter, applicationId);
        String invitation = jdbc.queryForObject("""
                select body from messages where application_id = ?::uuid and subject like 'Interview invitation%'
                 order by created_at desc limit 1""", String.class, applicationId);
        assertThat(invitation).contains("We missed you at your interview on");

        // Not progressing: the email waits 5 minutes, so the decision can be undone and the email cancelled.
        String decisions = postJson(recruiter, "/api/applications/" + applicationId + "/stage-decisions",
                Map.of("stage", "ROUND1", "decision", "REJECT", "reason", "Your notice period is longer than this role can wait for."));
        assertThat((String) JsonPath.read(decisions, "$[0].undoUntil")).isNotNull();
        String decisionId = JsonPath.read(decisions, "$[0].id");
        assertThat(jdbc.queryForObject("""
                select count(*) from messages where application_id = ?::uuid and subject like 'Your application%'
                   and sent_at is null and next_attempt_at > now()""", Integer.class, applicationId)).isEqualTo(1);
        mvc.perform(post("/api/stage-decisions/{id}/undo", decisionId).session(recruiter).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/applications/{id}", applicationId).session(recruiter))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.stage").value("ROUND1"));
        assertThat(jdbc.queryForObject("""
                select count(*) from messages where application_id = ?::uuid and subject like 'Your application%'
                   and cancelled_at is not null""", Integer.class, applicationId)).isEqualTo(1);

        // Closing the job cancels the upcoming interview and tells the candidate.
        mvc.perform(get("/api/jobs/{id}/close-impact", jobId).session(recruiter))
                .andExpect(jsonPath("$.interviewsToCancel").value(1))
                .andExpect(jsonPath("$.candidatesWithInterviews").value(1))
                .andExpect(jsonPath("$.otherCandidates").value(0));
        postJson(recruiter, "/api/jobs/" + jobId + "/status", Map.of("status", "CLOSED", "reason", "Role filled internally"));
        assertThat(jdbc.queryForObject("""
                select count(*) from interviews where application_id = ?::uuid and status = 'cancelled'
                   and cancel_reason like 'The job closed%'""", Integer.class, applicationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from messages where application_id = ?::uuid and subject like '%is cancelled'""",
                Integer.class, applicationId)).isEqualTo(1);
    }

    private String bookPanel(MockHttpSession session, String applicationId) throws Exception {
        String start = java.time.Instant.now().plus(java.time.Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString();
        return postJson(session, "/api/applications/" + applicationId + "/schedule/panel", Map.of(
                "startAt", start, "candidateZone", "Asia/Kolkata", "mode", "ONLINE",
                "meetingLink", "https://meet.example.com/abc-defg",
                "panel", List.of(Map.of("employeeId", "EMP-9", "fullName", "Test Recruiter", "email", recruiterEmail))));
    }

    private static Map<String, Object> round(String name, String kind, String mode, int minutes) {
        return Map.of("name", name, "kind", kind, "mode", mode, "durationMin", minutes, "targetDays", 5);
    }

    private String jobBody(String title, boolean ai, List<Map<String, Object>> rounds) {
        return json(Map.of(
                "title", title,
                "location", "Chennai",
                "contractType", "PERMANENT",
                "openings", 1,
                "minExperienceYrs", 4,
                "aiShortlisting", ai,
                "description", "We are hiring a cloud engineer to build and run our AWS platform with Terraform.",
                "criteria", List.of(
                        Map.of("kind", "MUST_HAVE", "value", "AWS"),
                        Map.of("kind", "MUST_HAVE", "value", "Terraform"),
                        Map.of("kind", "NICE_TO_HAVE", "value", "Kubernetes"),
                        Map.of("kind", "CERTIFICATION", "value", "AWS Solutions Architect - Associate")),
                "rounds", rounds));
    }

    private String createJob(MockHttpSession session, String title, boolean ai, List<Map<String, Object>> rounds) throws Exception {
        String jobId = read(mvc.perform(post("/api/jobs").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(jobBody(title, ai, rounds)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.aiShortlisting").value(ai))
                .andReturn(), "$.id");
        mvc.perform(post("/api/jobs/{id}/publish", jobId).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        return jobId;
    }

    private String uploadCv(MockHttpSession session, String jobId) throws Exception {
        byte[] cv = Files.readAllBytes(Path.of("src/main/resources/demo/cvs/Arjun_Nair_CV.pdf"));
        mvc.perform(multipart("/api/jobs/{id}/cvs", jobId)
                        .file(new MockMultipartFile("files", "Arjun_Nair_CV.pdf", "application/pdf", cv))
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                // The background reader may already have picked it up.
                .andExpect(jsonPath("$[0].status").value(org.hamcrest.Matchers.oneOf("QUEUED", "READING", "ADDED")));
        String upload = waitForUpload(session, jobId);
        assertThat((String) JsonPath.read(upload, "$[0].status")).isEqualTo("ADDED");
        assertThat((String) JsonPath.read(upload, "$[0].candidateName")).isEqualTo("Arjun Nair");
        return JsonPath.read(upload, "$[0].applicationId");
    }

    private String waitForUpload(MockHttpSession session, String jobId) throws Exception {
        for (int i = 0; i < 60; i++) {
            String body = mvc.perform(get("/api/jobs/{id}/cv-uploads", jobId).session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String state = JsonPath.read(body, "$[0].status");
            if (!state.equals("QUEUED") && !state.equals("READING")) {
                return body;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("The CV was not read in time");
    }

    private String postJson(MockHttpSession session, String path, Object body) throws Exception {
        return mvc.perform(post(path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    private void decide(MockHttpSession session, String applicationId, String stage) throws Exception {
        postJson(session, "/api/applications/" + applicationId + "/stage-decisions",
                Map.of("stage", stage, "decision", "ADVANCE", "reason", "Progressing after reviewing the report."));
    }

    private MockHttpSession signIn(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult step = passwordStep(session, email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value("totp_setup"))
                .andReturn();
        String secret = read(step, "$.secret");
        mvc.perform(post("/api/auth/totp").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", totp.currentCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        return session;
    }

    private org.springframework.test.web.servlet.ResultActions passwordStep(MockHttpSession session, String email,
                                                                          String password) throws Exception {
        return mvc.perform(post("/api/auth/login").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email, "password", password))));
    }

    private UUID seedCompany(String name, String country, String region) {
        return jdbc.queryForObject("insert into companies (name, country, data_region) values (?, ?, ?::data_region_t) returning id",
                UUID.class, name, country, region);
    }

    private UUID seedUser(UUID companyId, String email, String role) {
        String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD);
        return jdbc.queryForObject("""
                insert into hr_users (company_id, full_name, work_email, password_hash, role, status)
                values (?, ?, ?, ?, ?::hr_user_role_t, 'active') returning id""",
                UUID.class, companyId, email.startsWith("colleague") ? "Test colleague" : "Test " + role, email, hash, role);
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private static String json(Object value) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(value);
    }
}
