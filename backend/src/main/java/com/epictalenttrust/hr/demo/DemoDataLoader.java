package com.epictalenttrust.hr.demo;

import com.epictalenttrust.hr.application.ApplicationDtos.ApplicationRow;
import com.epictalenttrust.hr.application.ApplicationQueries;
import com.epictalenttrust.hr.application.ApplicationSource;
import com.epictalenttrust.hr.application.ApplicationStage;
import com.epictalenttrust.hr.application.CandidateService;
import com.epictalenttrust.hr.candidateportal.CandidateLinks;
import com.epictalenttrust.hr.candidateportal.CandidatePortalService;
import com.epictalenttrust.hr.company.Company;
import com.epictalenttrust.hr.company.CompanyDefaults;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.company.DataRegion;
import com.epictalenttrust.hr.cv.CvIntakeService;
import com.epictalenttrust.hr.hruser.HrUser;
import com.epictalenttrust.hr.hruser.HrUserRepository;
import com.epictalenttrust.hr.hruser.HrUserRole;
import com.epictalenttrust.hr.interview.InterviewDtos;
import com.epictalenttrust.hr.interview.InterviewDtos.InterviewRow;
import com.epictalenttrust.hr.interview.InterviewRepository;
import com.epictalenttrust.hr.interview.InterviewService;
import com.epictalenttrust.hr.interview.RescheduleReason;
import com.epictalenttrust.hr.interview.RoundSchedulingService;
import com.epictalenttrust.hr.interview.RoundSchedulingService.MemberInput;
import com.epictalenttrust.hr.job.ContractType;
import com.epictalenttrust.hr.job.CriterionKind;
import com.epictalenttrust.hr.job.InterviewMode;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobDtos;
import com.epictalenttrust.hr.job.JobDtos.CriterionInput;
import com.epictalenttrust.hr.job.JobDtos.RoundInput;
import com.epictalenttrust.hr.job.JobRepository;
import com.epictalenttrust.hr.job.JobService;
import com.epictalenttrust.hr.job.JobStatus;
import com.epictalenttrust.hr.job.RoundKind;
import com.epictalenttrust.hr.report.DecisionService;
import com.epictalenttrust.hr.report.ReportDtos.DecisionInput;
import com.epictalenttrust.hr.report.StageDecisionType;
import com.epictalenttrust.hr.routing.CentreOptionsService;
import com.epictalenttrust.hr.sample.SampleReportService;
import com.epictalenttrust.hr.security.Authorities;
import com.epictalenttrust.hr.security.HrUserPrincipal;
import com.epictalenttrust.hr.shortlist.ShortlistDecisionType;
import com.epictalenttrust.hr.shortlist.ShortlistService;
import com.epictalenttrust.hr.staff.EttStaff;
import com.epictalenttrust.hr.staff.EttStaffRepository;
import com.epictalenttrust.hr.staff.EttStaffRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a realistic sample client the first time the app starts with the "demo" profile. The sample CVs go through
 * the real upload, reading and scoring pipeline, and every later step uses the same services HR uses. Each job has
 * its own round plan, so all three round types appear:
 *
 * <ul>
 *   <li><b>AWS Cloud Engineer</b> (AI shortlisting on): Partner assessment → Technical Level 1 → Technical Level 2.
 *       Arjun has an offer to record; Farhan's Technical Level 2 report is in; Neha's Technical Level 1 panel can
 *       review now with the panel login; Divya's partner report is in; Kavya asked for a new time from her page;
 *       Meenakshi and Arun are ready to book in Madurai (nearby centres take turns); Tom is past his target time.</li>
 *   <li><b>Java Developer</b> (AI shortlisting off: every CV goes straight to Round 1): Technical Level 1 →
 *       Technical Level 2 → HR round. Emma owns it and is on leave; Oliver is covering.</li>
 *   <li><b>Data Analyst</b> (AI shortlisting on): AI interview → Partner assessment → HR round.</li>
 *   <li>An SRE job on hold, a closed Business Analyst job and an Azure draft.</li>
 * </ul>
 *
 * Interview reports are samples (labelled SAMPLE) until the AI interviewer, partner assessors and panels are connected.
 */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String PROGRESS = "Report reviewed with the hiring manager; progressing.";

    private final CompanyRepository companies;
    private final CompanyDefaults companyDefaults;
    private final HrUserRepository users;
    private final EttStaffRepository staff;
    private final PasswordEncoder passwordEncoder;
    private final JobService jobs;
    private final JobRepository jobRepository;
    private final CvIntakeService uploads;
    private final ApplicationQueries applicationQueries;
    private final ShortlistService shortlist;
    private final RoundSchedulingService rounds;
    private final InterviewService interviewService;
    private final InterviewRepository interviewRepository;
    private final SampleReportService samples;
    private final DecisionService decisions;
    private final CandidateService candidateService;
    private final CentreOptionsService centreOptions;
    private final CandidateLinks candidateLinks;
    private final CandidatePortalService candidatePortal;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String demoPassword;
    private final com.epictalenttrust.hr.security.SecretCipher cipher;

    /**
     * For trying the panel login: this person is on four panels, each with a fixed interview code, listed in
     * RUN-WITH-DOCKER.txt and the README. Neha's and Mohammed's reviews are waiting, Farhan's is sent, and Sneha's
     * interview is tomorrow.
     */
    static final MemberInput DEMO_MEMBER = new MemberInput("EMP-1001", "Demo Panel Member", "demo.panel@pellhamrowe.example");

    public DemoDataLoader(CompanyRepository companies, CompanyDefaults companyDefaults, HrUserRepository users,
                          EttStaffRepository staff, PasswordEncoder passwordEncoder, JobService jobs,
                          JobRepository jobRepository, CvIntakeService uploads, ApplicationQueries applicationQueries,
                          ShortlistService shortlist, RoundSchedulingService rounds, InterviewService interviewService,
                          InterviewRepository interviewRepository, SampleReportService samples, DecisionService decisions,
                          CandidateService candidateService, CentreOptionsService centreOptions,
                          CandidateLinks candidateLinks, CandidatePortalService candidatePortal, JdbcClient jdbc,
                          PlatformTransactionManager transactionManager, Clock clock,
                          @Value("${ett.demo.password:Welcome@2026!}") String demoPassword,
                          com.epictalenttrust.hr.security.SecretCipher cipher) {
        this.cipher = cipher;
        this.companies = companies;
        this.companyDefaults = companyDefaults;
        this.users = users;
        this.staff = staff;
        this.passwordEncoder = passwordEncoder;
        this.jobs = jobs;
        this.jobRepository = jobRepository;
        this.uploads = uploads;
        this.applicationQueries = applicationQueries;
        this.shortlist = shortlist;
        this.rounds = rounds;
        this.interviewService = interviewService;
        this.interviewRepository = interviewRepository;
        this.samples = samples;
        this.decisions = decisions;
        this.candidateService = candidateService;
        this.centreOptions = centreOptions;
        this.candidateLinks = candidateLinks;
        this.candidatePortal = candidatePortal;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (companies.count() > 0) {
            return;
        }
        log.info("Loading demo data...");
        String hash = passwordEncoder.encode(demoPassword);

        Company client = companies.save(new Company("Pellham & Rowe", "India", DataRegion.APAC));
        HrUser charlotte = user(client, "Charlotte Hughes", "charlotte.hughes", HrUserRole.ADMIN, hash);
        user(client, "James Whitaker", "james.whitaker", HrUserRole.ADMIN, hash);
        HrUser oliver = user(client, "Oliver Grant", "oliver.grant", HrUserRole.RECRUITER, hash);
        HrUser emma = user(client, "Emma Lawson", "emma.lawson", HrUserRole.RECRUITER, hash);
        user(client, "Thomas Reid", "thomas.reid", HrUserRole.RECRUITER, hash);
        HrUser daniel = user(client, "Daniel Fletcher", "daniel.fletcher", HrUserRole.HIRING_MANAGER, hash);
        HrUser sarah = user(client, "Sarah Mitchell", "sarah.mitchell", HrUserRole.HIRING_MANAGER, hash);
        users.save(withPassword(new HrUser(client.getId(), "Interview Panel", "panel@pellhamrowe.example",
                HrUserRole.PANEL), hash));
        staff.save(new EttStaff("Anita Rao", "anita.rao@epictalenttrust.example", EttStaffRole.REVIEWER));
        staff.save(new EttStaff("Vikram Shah", "vikram.shah@epictalenttrust.example", EttStaffRole.ADMIN));
        transaction.executeWithoutResult(status -> {
            jdbc.sql("update companies set address = :a, time_zone = 'Asia/Kolkata' where id = :c")
                    .param("a", "12th Floor, Prestige Tech Park, Outer Ring Road, Bengaluru 560103")
                    .param("c", client.getId()).update();
            companyDefaults.apply(client.getId());
            saveLogo(client.getId(), charlotte.getId());
            partnerCentres(client.getId());
        });

        actAs(oliver, () -> awsJob(oliver, daniel));
        actAs(emma, () -> javaJob(emma, sarah));
        actAs(oliver, () -> dataJob(oliver, daniel));
        actAs(charlotte, this::otherJobs);
        transaction.executeWithoutResult(status -> leaveCover(client.getId(), emma, oliver, charlotte));

        log.info("Demo data ready. Sign in as charlotte.hughes@pellhamrowe.example with the demo password; "
                + "you will set up an authenticator app on first sign-in.");
    }

    // ------------------------------------------------------------------ AWS Cloud Engineer

    private void awsJob(HrUser owner, HrUser hiringManager) {
        var job = jobs.create(new JobDtos.JobRequest("AWS Cloud Engineer", "Bengaluru · Chennai · Madurai · Hyderabad",
                ContractType.PERMANENT, 2, 4,
                List.of(new CriterionInput(CriterionKind.MUST_HAVE, "AWS", 3),
                        new CriterionInput(CriterionKind.MUST_HAVE, "Terraform"),
                        new CriterionInput(CriterionKind.MUST_HAVE, "IAM"),
                        new CriterionInput(CriterionKind.MUST_HAVE, "CI/CD"),
                        new CriterionInput(CriterionKind.NICE_TO_HAVE, "Kubernetes"),
                        new CriterionInput(CriterionKind.NICE_TO_HAVE, "Python"),
                        new CriterionInput(CriterionKind.NICE_TO_HAVE, "CloudWatch"),
                        new CriterionInput(CriterionKind.CERTIFICATION, "AWS Solutions Architect - Associate")),
                List.of(new RoundInput("Partner assessment centre", RoundKind.PARTNER, InterviewMode.IN_PERSON, 60, 5),
                        new RoundInput("Technical Level 1", RoundKind.PANEL, InterviewMode.ONLINE, 60, 3),
                        new RoundInput("Technical Level 2", RoundKind.PANEL, InterviewMode.ONLINE, 60, 5)),
                60, 30.0, List.of("Bengaluru", "Chennai", "Madurai", "Hyderabad", "Kochi"), null, true,
                AWS_JD, null, hiringManager.getId(), owner.getId()));
        jobs.publish(job.id());
        UUID jobId = job.id();
        upload(jobId, "classpath:demo/cvs/*", owner);
        Map<String, ApplicationRow> c = byName(jobId);

        // Shortlisting. Sanjay and Ananya wait for a decision; Vikram has red flags; Suresh's CV needs details.
        for (String name : List.of("Arjun Nair", "Kavya Menon", "Farhan Ali", "Emily Clarke", "Neha Kulkarni", "Divya Raj",
                "Meenakshi Sundaram", "Arun Pandian")) {
            decide(c.get(name), ShortlistDecisionType.SHORTLISTED, "Strong match on the must-haves; worth an interview.");
        }
        decide(c.get("Tom Bennett"), ShortlistDecisionType.SHORTLISTED,
                "Strong architecture background. The partner assessment will check hands-on Terraform.");
        decide(c.get("Rohit Sharma"), ShortlistDecisionType.HOLD, "Keep in reserve until the first round is done.");
        for (String name : List.of("Aisha Khan", "Liam Walsh", "Priya Das")) {
            decide(c.get(name), ShortlistDecisionType.REJECTED, "Not enough hands-on AWS work for this role.");
        }
        shortlist.finishReview(jobId);

        // Round 1: partner assessment, in person (sample reports for Arjun, Farhan, Neha and Divya).
        int[] daysAgo = {22, 20, 12, 4};
        String[] names = {"Arjun Nair", "Farhan Ali", "Neha Kulkarni", "Divya Raj"};
        for (int i = 0; i < names.length; i++) {
            ApplicationRow row = c.get(names[i]);
            if (row != null) {
                InterviewRow interview = bookPartner(row.id());
                moveToPast(interview.id(), daysAgo[i], slotOf(interview.id()));
                interviewService.accept(interview.id());
                samples.completeNow(interview.id());
            }
        }
        ApplicationRow emily = c.get("Emily Clarke");
        if (emily != null) {
            interviewService.accept(bookPartner(emily.id()).id());
        }
        ApplicationRow kavya = c.get("Kavya Menon");
        if (kavya != null) {
            InterviewRow interview = bookPartner(kavya.id());
            askForNewTimeFromCandidatePage(kavya.id(), interview.id());
        }
        // Tom: shortlisted a week ago and still not booked: past the 5-day target.
        backdateStage(c.get("Tom Bennett"), 7);
        for (String name : List.of("Arjun Nair", "Farhan Ali", "Neha Kulkarni")) {
            advance(c.get(name), ApplicationStage.ROUND1);
        }

        // Round 2: Technical Level 1, panel online.
        List<MemberInput> panelA = List.of(new MemberInput("EMP-1043", "Rohan Mehta", "rohan.mehta@pellhamrowe.example"),
                new MemberInput("EMP-2210", "Laura Bennett", "laura.bennett@pellhamrowe.example"));
        List<MemberInput> panelB = List.of(new MemberInput("EMP-1043", "Rohan Mehta", "rohan.mehta@pellhamrowe.example"),
                new MemberInput("EMP-3307", "Fatima Sheikh", "fatima.sheikh@pellhamrowe.example"));
        int[] l1DaysAgo = {15, 13};
        String[] l1 = {"Arjun Nair", "Farhan Ali"};
        for (int i = 0; i < l1.length; i++) {
            ApplicationRow row = c.get(l1[i]);
            if (row != null) {
                InterviewRow interview = bookPanel(row.id(), panelA, at(2 + i, LocalTime.of(11, 0)), 60);
                moveToPast(interview.id(), l1DaysAgo[i], null);
                interviewService.accept(interview.id());
                samples.completeNow(interview.id());
                advance(row, ApplicationStage.ROUND2);
            }
        }
        // Neha's Technical Level 1 finished two hours ago: the panel can sign in and review it now.
        ApplicationRow neha = c.get("Neha Kulkarni");
        if (neha != null) {
            InterviewRow interview = bookPanel(neha.id(), withDemoMember(panelB), at(2, LocalTime.of(15, 0)), 60);
            interviewService.accept(interview.id());
            startedHoursAgo(interview.id(), 2);
            fixCode(interview.id(), "NEHA-4825");
        }

        // Round 3: Technical Level 2, panel online. Arjun passed (offer to record); Farhan's reviews are in.
        List<MemberInput> panelC = List.of(new MemberInput("EMP-0981", "Vikram Desai", "vikram.desai@pellhamrowe.example"),
                new MemberInput("EMP-2210", "Laura Bennett", "laura.bennett@pellhamrowe.example"),
                new MemberInput("EMP-4120", "Alistair Moore", "alistair.moore@pellhamrowe.example"));
        int[] l2DaysAgo = {6, 1};
        for (int i = 0; i < l1.length; i++) {
            ApplicationRow row = c.get(l1[i]);
            if (row != null) {
                boolean farhan = l1[i].equals("Farhan Ali");
                InterviewRow interview = bookPanel(row.id(), farhan ? withDemoMember(panelC) : panelC,
                        at(3 + i, LocalTime.of(16, 0)), 60);
                moveToPast(interview.id(), l2DaysAgo[i], null);
                interviewService.accept(interview.id());
                samples.completeNow(interview.id());
                if (farhan) {
                    fixCode(interview.id(), "FARH-5938");
                }
            }
        }
        advance(c.get("Arjun Nair"), ApplicationStage.ROUND3);
        ApplicationRow arjun = c.get("Arjun Nair");
        if (arjun != null) {
            candidateService.addNote(arjun.id(), "Spoke to Arjun on the phone: happy with the offer range, needs 60 days' notice.");
        }
    }

    // ------------------------------------------------------------------ Java Developer (AI shortlisting off)

    private void javaJob(HrUser owner, HrUser hiringManager) {
        var job = jobs.create(new JobDtos.JobRequest("Java Developer", "Chennai · Bengaluru · Hyderabad",
                ContractType.PERMANENT, 3, 2,
                List.of(new CriterionInput(CriterionKind.MUST_HAVE, "Java", 2),
                        new CriterionInput(CriterionKind.MUST_HAVE, "Spring Boot"),
                        new CriterionInput(CriterionKind.MUST_HAVE, "REST APIs"),
                        new CriterionInput(CriterionKind.MUST_HAVE, "SQL"),
                        new CriterionInput(CriterionKind.NICE_TO_HAVE, "Kafka"),
                        new CriterionInput(CriterionKind.NICE_TO_HAVE, "Docker"),
                        new CriterionInput(CriterionKind.NICE_TO_HAVE, "Microservices")),
                List.of(new RoundInput("Technical Level 1", RoundKind.PANEL, InterviewMode.ONLINE, 60, 3),
                        new RoundInput("Technical Level 2", RoundKind.PANEL, InterviewMode.ONLINE, 90, 5),
                        new RoundInput("HR round", RoundKind.PANEL, InterviewMode.IN_PERSON, 30, 3)),
                60, 24.0, List.of("Chennai", "Bengaluru", "Hyderabad", "Madurai"), null, false, JAVA_JD, null,
                hiringManager.getId(), owner.getId()));
        jobs.publish(job.id());
        UUID jobId = job.id();
        upload(jobId, "classpath:demo/cvs-java/*", owner);
        Map<String, ApplicationRow> c = byName(jobId);
        List<MemberInput> panel = List.of(new MemberInput("EMP-1188", "Suresh Iyer", "suresh.iyer@pellhamrowe.example"),
                new MemberInput("EMP-2502", "Grace Holloway", "grace.holloway@pellhamrowe.example"));
        List<MemberInput> senior = List.of(new MemberInput("EMP-0420", "Natasha Pillai", "natasha.pillai@pellhamrowe.example"),
                new MemberInput("EMP-1188", "Suresh Iyer", "suresh.iyer@pellhamrowe.example"));

        ApplicationRow karthik = c.get("Karthik Subramanian");
        if (karthik != null) {
            InterviewRow l1 = bookPanel(karthik.id(), panel, at(2, LocalTime.of(10, 0)), 60);
            moveToPast(l1.id(), 9, null);
            interviewService.accept(l1.id());
            samples.completeNow(l1.id());
            advance(karthik, ApplicationStage.ROUND1);
            InterviewRow l2 = bookPanel(karthik.id(), senior, at(3, LocalTime.of(14, 30)), 90);
            moveToPast(l2.id(), 2, null);
            interviewService.accept(l2.id());
            samples.completeNow(l2.id());
            // Reached Technical Level 2 six days ago: the decision is now past the 5-day target.
            backdateStage(karthik, 6);
        }
        ApplicationRow irfan = c.get("Mohammed Irfan");
        if (irfan != null) {
            InterviewRow l1 = bookPanel(irfan.id(), withDemoMember(panel), at(2, LocalTime.of(12, 0)), 60);
            moveToPast(l1.id(), 1, null);
            interviewService.accept(l1.id());
            samples.completeNow(l1.id());
            // The other panel members have sent their reviews; the demo panel member's is still to do.
            transaction.executeWithoutResult(status -> jdbc.sql("""
                    delete from panel_reviews where panel_member_id in
                           (select id from panel_members where interview_id = :i and employee_id = :e)
                    """).param("i", l1.id()).param("e", DEMO_MEMBER.employeeId()).update());
            fixCode(l1.id(), "MHMD-6372");
        }
        ApplicationRow sneha = c.get("Sneha Iyer");
        if (sneha != null) {
            InterviewRow tomorrow = bookPanel(sneha.id(), withDemoMember(panel), at(1, LocalTime.of(11, 30)), 60);
            interviewService.accept(tomorrow.id());
            fixCode(tomorrow.id(), "SNEH-2749");
        }
        // Later today, online: Charlotte is on Lakshmi's Technical Level 1 panel and Oliver on Ben's, so each sees
        // a Join button on their Overview (highlighted from 15 minutes before the start).
        ApplicationRow lakshmi = c.get("Lakshmi Narayanan");
        if (lakshmi != null) {
            InterviewRow today = bookPanel(lakshmi.id(), List.of(
                            new MemberInput("EMP-0102", "Charlotte Hughes", "charlotte.hughes@pellhamrowe.example"),
                            new MemberInput("EMP-1188", "Suresh Iyer", "suresh.iyer@pellhamrowe.example")),
                    at(1, LocalTime.of(10, 0)), 60);
            interviewService.accept(today.id());
            moveStart(today.id(), laterToday(120));
        }
        ApplicationRow ben = c.get("Ben Hartley");
        if (ben != null) {
            InterviewRow today = bookPanel(ben.id(), List.of(
                            new MemberInput("EMP-0215", "Oliver Grant", "oliver.grant@pellhamrowe.example"),
                            new MemberInput("EMP-2502", "Grace Holloway", "grace.holloway@pellhamrowe.example")),
                    at(1, LocalTime.of(15, 0)), 60);
            interviewService.accept(today.id());
            moveStart(today.id(), laterToday(210));
        }
        // Pooja reached Round 1 five days ago and isn't booked yet: past the 3-day target.
        backdateStage(c.get("Pooja Verma"), 5);
    }

    // ------------------------------------------------------------------ Data Analyst

    private void dataJob(HrUser owner, HrUser hiringManager) {
        var job = jobs.create(new JobDtos.JobRequest("Data Analyst", "Bengaluru · Coimbatore · Pune · London",
                ContractType.PERMANENT, 2, 2,
                List.of(new CriterionInput(CriterionKind.MUST_HAVE, "SQL", 2),
                        new CriterionInput(CriterionKind.MUST_HAVE, "Power BI"),
                        new CriterionInput(CriterionKind.MUST_HAVE, "Excel"),
                        new CriterionInput(CriterionKind.NICE_TO_HAVE, "Python"),
                        new CriterionInput(CriterionKind.NICE_TO_HAVE, "Tableau")),
                List.of(new RoundInput("AI interview", RoundKind.AI_INTERVIEW, InterviewMode.ONLINE, 45, 3),
                        new RoundInput("Partner assessment centre", RoundKind.PARTNER, InterviewMode.IN_PERSON, 60, 5),
                        new RoundInput("HR round", RoundKind.PANEL, InterviewMode.ONLINE, 30, 3)),
                45, 18.0, List.of("Bengaluru", "Coimbatore", "Pune", "London"), null, true, DATA_JD, null,
                hiringManager.getId(), owner.getId()));
        jobs.publish(job.id());
        UUID jobId = job.id();
        upload(jobId, "classpath:demo/cvs-data/*", owner);
        Map<String, ApplicationRow> c = byName(jobId);
        for (String name : List.of("Ananth Krishnan", "Sophie Turner", "Divya Prakash", "Revathi Murugan", "Aditya Kumar")) {
            decide(c.get(name), ShortlistDecisionType.SHORTLISTED, "Good match on SQL and reporting tools.");
        }
        ApplicationRow ananth = c.get("Ananth Krishnan");
        if (ananth != null) {
            InterviewRow ai = rounds.scheduleAi(ananth.id(), new RoundSchedulingService.AiInput(
                    at(2, LocalTime.of(10, 30)), "Asia/Kolkata", 45, null));
            moveToPast(ai.id(), 6, null);
            interviewService.accept(ai.id());
            samples.completeNow(ai.id());
            advance(ananth, ApplicationStage.ROUND1);
        }
        ApplicationRow sophie = c.get("Sophie Turner");
        if (sophie != null) {
            InterviewRow ai = rounds.scheduleAi(sophie.id(), new RoundSchedulingService.AiInput(
                    at(2, LocalTime.of(14, 0)), "Europe/London", 45, null));
            moveToPast(ai.id(), 2, null);
            interviewService.accept(ai.id());
            samples.completeNow(ai.id());
        }
        ApplicationRow divya = c.get("Divya Prakash");
        if (divya != null) {
            rounds.scheduleAi(divya.id(), new RoundSchedulingService.AiInput(
                    at(2, LocalTime.of(11, 0)), "Asia/Kolkata", 45, null));
        }
        // Revathi's CV gives no location: the AI interviewer asked, she's in a town near Madurai, so her partner
        // assessment centre round offers only the Madurai centres.
        ApplicationRow revathi = c.get("Revathi Murugan");
        if (revathi != null) {
            InterviewRow ai = rounds.scheduleAi(revathi.id(), new RoundSchedulingService.AiInput(
                    at(2, LocalTime.of(9, 30)), "Asia/Kolkata", 45, null));
            moveToPast(ai.id(), 3, null);
            interviewService.accept(ai.id());
            samples.completeNow(ai.id());
            advance(revathi, ApplicationStage.ROUND1);
        }
        // Aditya's CV gives no location either: it will be asked in his AI interview, booked for later this week.
        ApplicationRow aditya = c.get("Aditya Kumar");
        if (aditya != null) {
            rounds.scheduleAi(aditya.id(), new RoundSchedulingService.AiInput(
                    at(3, LocalTime.of(15, 0)), "Asia/Kolkata", 45, null));
        }
    }

    // ------------------------------------------------------------------ other jobs

    private void otherJobs() {
        var sre = jobs.create(new JobDtos.JobRequest("Site Reliability Engineer", "Bengaluru", ContractType.PERMANENT, 1, 5,
                List.of(new CriterionInput(CriterionKind.MUST_HAVE, "Kubernetes"),
                        new CriterionInput(CriterionKind.MUST_HAVE, "Linux"),
                        new CriterionInput(CriterionKind.NICE_TO_HAVE, "Go")),
                null, 60, 40.0, List.of("Bengaluru"), null));
        jobs.publish(sre.id());
        jobs.changeStatus(sre.id(), new JobDtos.StatusChange(JobStatus.ON_HOLD,
                "Budget review for the platform team. Restarting in January."));

        var ba = jobs.create(new JobDtos.JobRequest("Business Analyst", "Chennai", ContractType.FIXED_TERM, 1, 3,
                List.of(new CriterionInput(CriterionKind.MUST_HAVE, "Requirements gathering"),
                        new CriterionInput(CriterionKind.MUST_HAVE, "SQL")),
                null, 30, 15.0, List.of("Chennai"), null));
        jobs.publish(ba.id());
        jobs.changeStatus(ba.id(), new JobDtos.StatusChange(JobStatus.CLOSED, "Filled internally by a transfer."));

        var azure = jobs.create(new JobDtos.JobRequest("Azure Data Engineer", "Bengaluru", ContractType.PERMANENT, 1, 3,
                List.of(new CriterionInput(CriterionKind.MUST_HAVE, "Azure"),
                        new CriterionInput(CriterionKind.MUST_HAVE, "SQL"),
                        new CriterionInput(CriterionKind.MUST_HAVE, "Python"),
                        new CriterionInput(CriterionKind.NICE_TO_HAVE, "Databricks")),
                null, 60, 28.0, List.of("Bengaluru"), null));
        log.debug("Draft job {} created", azure.id());
    }

    /** Emma is on leave this week; Oliver covers her jobs. James has leave coming up, covered by Charlotte. */
    private void leaveCover(UUID companyId, HrUser emma, HrUser oliver, HrUser charlotte) {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        jdbc.sql("""
                insert into leave_cover (company_id, user_id, cover_user_id, starts_on, ends_on, note, created_by)
                values (:c, :u, :cu, :s, :e, :n, :u)
                """)
                .param("c", companyId).param("u", emma.getId()).param("cu", oliver.getId())
                .param("s", today.minusDays(1)).param("e", today.plusDays(6))
                .param("n", "Back next Tuesday. Karthik's Technical Level 2 reviews are in and need a decision.").update();
        UUID james = jdbc.sql("select id from hr_users where work_email = 'james.whitaker@pellhamrowe.example'")
                .query(UUID.class).single();
        jdbc.sql("""
                insert into leave_cover (company_id, user_id, cover_user_id, starts_on, ends_on, note, created_by)
                values (:c, :u, :cu, :s, :e, null, :u)
                """)
                .param("c", companyId).param("u", james).param("cu", charlotte.getId())
                .param("s", today.plusDays(10)).param("e", today.plusDays(17)).update();
    }

    // ------------------------------------------------------------------ steps

    private void upload(UUID jobId, String pattern, HrUser by) {
        for (Resource cv : resources(pattern)) {
            try {
                String name = cv.getFilename();
                ApplicationSource source = name.startsWith("Kavya") || name.startsWith("Vikram") || name.startsWith("Sneha")
                        ? ApplicationSource.JOB_BOARD : name.startsWith("Tom") || name.startsWith("Ben")
                        ? ApplicationSource.AGENCY : ApplicationSource.HR_UPLOAD;
                uploads.addNow(jobEntity(jobId), name, cv.getContentAsByteArray(), by.getId(), source);
            } catch (IOException ex) {
                throw new IllegalStateException("Could not read demo CV " + cv.getFilename(), ex);
            }
        }
    }

    private Map<String, ApplicationRow> byName(UUID jobId) {
        return applicationQueries.forJob(jobId).stream()
                .collect(Collectors.toMap(ApplicationRow::candidateName, r -> r, (a, b) -> a));
    }

    private void decide(ApplicationRow row, ShortlistDecisionType decision, String reason) {
        if (row == null) {
            return;
        }
        boolean agrees = row.suggestion() != null && ShortlistService.agrees(row.suggestion(), decision);
        shortlist.decide(row.id(), decision, agrees
                && row.suggestion() != com.epictalenttrust.hr.screening.AiSuggestion.REVIEW ? null : reason);
    }

    private void advance(ApplicationRow row, ApplicationStage stage) {
        if (row != null) {
            decisions.decide(row.id(), new DecisionInput(stage, StageDecisionType.ADVANCE, PROGRESS));
        }
    }

    /** Books the centre that's next in turn and its first suitable slot, as HR would by taking the suggestion. */
    private InterviewRow bookPartner(UUID applicationId) {
        var centre = centreOptions.options(applicationId, null, null, false).centres().stream()
                .filter(cv -> cv.recommended() && cv.firstSuitableSlotId() != null).findFirst()
                .or(() -> centreOptions.options(applicationId, null, null, false).centres().stream()
                        .filter(cv -> cv.firstSuitableSlotId() != null).findFirst())
                .orElseThrow();
        var slot = centre.slots().stream().filter(sl -> sl.id().equals(centre.firstSuitableSlotId())).findFirst().orElseThrow();
        return rounds.schedulePartner(applicationId, new RoundSchedulingService.PartnerInput(
                slot.id(), slot.freeAssessorIds().getFirst(), null, null));
    }

    private static List<MemberInput> withDemoMember(List<MemberInput> panel) {
        List<MemberInput> out = new ArrayList<>(panel);
        out.add(DEMO_MEMBER);
        return out;
    }

    /** Gives a sample interview a fixed code, so it can be written down in the instructions. */
    private void fixCode(UUID interviewId, String code) {
        transaction.executeWithoutResult(status -> jdbc.sql(
                        "update interviews set panel_code_hash = :h, panel_code_enc = :e where id = :id")
                .param("h", RoundSchedulingService.hashCode(code)).param("e", cipher.encrypt(code))
                .param("id", interviewId).update());
    }

    private InterviewRow bookPanel(UUID applicationId, List<MemberInput> panel, Instant start, int minutes) {
        return rounds.schedulePanel(applicationId, new RoundSchedulingService.PanelInput(start, "Asia/Kolkata", minutes,
                InterviewMode.ONLINE, "https://meet.example.com/pr-" + applicationId.toString().substring(0, 8), null,
                panel, null));
    }

    /** Kavya used the link in her invitation to ask for another time and say which days she's free. */
    private void askForNewTimeFromCandidatePage(UUID applicationId, UUID interviewId) {
        String link = transaction.execute(status -> candidateLinks.issue(applicationId,
                interviewRepository.findById(interviewId).orElseThrow()));
        String token = link.substring(link.lastIndexOf('/') + 1);
        LocalDate start = LocalDate.now(clock.withZone(IST)).plusDays(6);
        List<LocalDate> days = start.datesUntil(start.plusDays(10)).filter(d -> d.getDayOfWeek().getValue() <= 5)
                .limit(3).toList();
        candidatePortal.askForNewTime(token, new CandidatePortalService.NewTimeInput(RescheduleReason.CLASH,
                "Our team has a production release that day. Sorry for the short notice.",
                List.of(new InterviewDtos.Availability(days.get(0), "morning", null, null),
                        new InterviewDtos.Availability(days.get(1), "range", "14:00", "17:00"),
                        new InterviewDtos.Availability(days.get(2), "any", null, null))));
    }

    /** Demo only: the candidate reached their current round some days ago (for target times). */
    private void backdateStage(ApplicationRow row, int days) {
        if (row == null) {
            return;
        }
        transaction.executeWithoutResult(status -> jdbc.sql("update applications set stage_entered_at = :at where id = :id")
                .param("at", Timestamp.from(clock.instant().minus(Duration.ofDays(days)))).param("id", row.id()).update());
    }

    /** Demo only: the interview took place some days ago. */
    private void moveToPast(UUID interviewId, int daysAgo, UUID slotId) {
        Instant start = ZonedDateTime.now(clock.withZone(IST)).minusDays(daysAgo)
                .with(LocalTime.of(daysAgo % 2 == 0 ? 11 : 15, 0)).toInstant();
        transaction.executeWithoutResult(status -> {
            jdbc.sql("""
                    update interviews set start_at = :start, reply_by = :reply, invited_at = :invited,
                           arrive_by = case when arrive_by is null then null else cast(:arrive as timestamptz) end
                     where id = :id
                    """)
                    .param("start", Timestamp.from(start)).param("reply", Timestamp.from(start.minus(Duration.ofDays(2))))
                    .param("invited", Timestamp.from(start.minus(Duration.ofDays(4))))
                    .param("arrive", Timestamp.from(start.minus(Duration.ofMinutes(15)))).param("id", interviewId).update();
            if (slotId != null) {
                jdbc.sql("update centre_slots set start_at = :s, end_at = :e where id = :id")
                        .param("s", Timestamp.from(start)).param("e", Timestamp.from(start.plus(Duration.ofHours(1))))
                        .param("id", slotId).update();
            }
        });
    }

    /** Demo only: a panel interview that ended a little while ago, with no reviews yet. */
    private void startedHoursAgo(UUID interviewId, int hours) {
        Instant start = clock.instant().minus(Duration.ofHours(hours)).truncatedTo(ChronoUnit.MINUTES);
        transaction.executeWithoutResult(status -> jdbc.sql("""
                        update interviews set start_at = :s, reply_by = :r, invited_at = :i, status = 'completed',
                               completed_at = :done where id = :id
                        """)
                .param("s", Timestamp.from(start)).param("r", Timestamp.from(start.minus(Duration.ofDays(1))))
                .param("i", Timestamp.from(start.minus(Duration.ofDays(3))))
                .param("done", Timestamp.from(start.plus(Duration.ofHours(1)))).param("id", interviewId).update());
    }

    private UUID slotOf(UUID interviewId) {
        return jdbc.sql("select slot_id from interviews where id = :id").param("id", interviewId).query(UUID.class).single();
    }

    // ------------------------------------------------------------------ reference data

    private void saveLogo(UUID companyId, UUID by) {
        try {
            byte[] logo = new ClassPathResource("demo/logo.png").getContentAsByteArray();
            jdbc.sql("""
                    insert into company_logos (company_id, content_type, file_name, image, created_by, last_modified_by)
                    values (:c, 'image/png', 'pellham-rowe-logo.png', :img, :u, :u)
                    """)
                    .param("c", companyId).param("img", logo).param("u", by).update();
        } catch (IOException ex) {
            log.warn("Demo logo missing: {}", ex.getMessage());
        }
    }

    /**
     * Partner assessment centres with map positions and seats on weekdays for the next three weeks, in each centre's
     * own time zone. The company manages its own centres (Madurai, Chennai, Bengaluru); the rest are ETT's shared
     * network. Madurai has three centres that take turns: ELCOT IT Park had a candidate longest ago, so it is next,
     * then K K Nagar (fully booked for its first two days), then Anna Nagar, which had the last one.
     */
    private void partnerCentres(UUID companyId) {
        record Centre(String name, String address, String city, String postcode, String country, double lat, double lon,
                      String zone, String org, int seats, int busyDays, boolean own, List<String> assessors) {
        }
        String in = "India";
        String uk = "United Kingdom";
        String ist = "Asia/Kolkata";
        String london = "Europe/London";
        List<Centre> centres = List.of(
                new Centre("SkillBridge Madurai – Anna Nagar", "Plot 14, 80 Feet Road, Anna Nagar", "Madurai", "625020", in,
                        9.9259, 78.1453, ist, "SkillBridge Partners", 2, 0, true, List.of("Murugan Pandi", "Kavitha Raman")),
                new Centre("TalentHub Madurai – K K Nagar", "2nd Floor, Lake View Complex, K K Nagar", "Madurai", "625020", in,
                        9.9348, 78.1440, ist, "TalentHub Assessments", 1, 2, true, List.of("Selvi Arumugam")),
                new Centre("AssessPoint Madurai – ELCOT IT Park", "Block B, ELCOT IT Park, Ilanthaikulam", "Madurai", "625020", in,
                        9.9440, 78.1600, ist, "AssessPoint India", 3, 0, true, List.of("Ganesh Muthu", "Revathi Kannan")),
                new Centre("TalentHub Coimbatore – Peelamedu", "Tidel Park, Avinashi Road, Peelamedu", "Coimbatore", "641004", in,
                        11.0257, 77.0200, ist, "TalentHub Assessments", 2, 0, false, List.of("Prakash Velu")),
                new Centre("SkillBridge Trichy – Thillai Nagar", "5th Cross, Thillai Nagar", "Tiruchirappalli", "620018", in,
                        10.8250, 78.6830, ist, "SkillBridge Partners", 2, 0, false, List.of("Anitha Krishnan")),
                new Centre("TalentHub Chennai – Guindy", "2nd Floor, Olympia Tech Park, Guindy", "Chennai", "600032", in,
                        13.0108, 80.2155, ist, "TalentHub Assessments", 2, 0, true, List.of("Senthil Kumar", "Lakshmi Priya")),
                new Centre("SkillBridge Bengaluru – Koramangala", "4th Floor, Prestige Tower, 80 Feet Road, Koramangala",
                        "Bengaluru", "560034", in, 12.9352, 77.6245, ist, "SkillBridge Partners", 2, 0, true,
                        List.of("Ramesh Gowda", "Lakshmi Narayan")),
                new Centre("TalentHub Hyderabad – HITEC City", "Level 3, Cyber Towers, HITEC City", "Hyderabad", "500081", in,
                        17.4504, 78.3808, ist, "TalentHub Assessments", 2, 0, false, List.of("Aparna Rao")),
                new Centre("SkillBridge Pune – Hinjewadi", "Unit 5, Embassy Tech Zone, Hinjewadi Phase 2", "Pune", "411057", in,
                        18.5913, 73.7389, ist, "SkillBridge Partners", 2, 0, false, List.of("Nikhil Deshpande")),
                new Centre("TalentHub Mumbai – Andheri East", "8th Floor, Times Square, Andheri-Kurla Road", "Mumbai", "400059", in,
                        19.1136, 72.8697, ist, "TalentHub Assessments", 2, 0, false, List.of("Farida Shaikh")),
                new Centre("SkillBridge Kochi – Infopark", "Phase 1, Infopark, Kakkanad", "Kochi", "682042", in,
                        10.0107, 76.3628, ist, "SkillBridge Partners", 2, 0, false, List.of("Joseph Mathew")),
                new Centre("TalentHub London – Farringdon", "3rd Floor, 25 Farringdon Road", "London", "EC1M 3HA", uk,
                        51.5203, -0.1053, london, "TalentHub Assessments", 2, 0, false, List.of("Rebecca Shaw", "Matthew Collins")),
                new Centre("SkillBridge Leeds – Wellington Place", "1 Wellington Place", "Leeds", "LS1 4AP", uk,
                        53.7965, -1.5560, london, "SkillBridge Partners", 2, 0, false, List.of("Hannah Price")),
                new Centre("TalentHub Manchester – Spinningfields", "4 Hardman Square", "Manchester", "M3 3EB", uk,
                        53.4800, -2.2510, london, "TalentHub Assessments", 2, 0, false, List.of("Peter Doyle")));
        for (Centre centre : centres) {
            UUID id = jdbc.sql("""
                            insert into partner_centres (name, address, city, postcode, country, latitude, longitude,
                                                         time_zone, step_free_access, partner_org, company_id)
                            values (:n, :a, :c, :p, :country, :lat, :lon, :tz, true, :o, :company) returning id
                            """)
                    .param("n", centre.name()).param("a", centre.address()).param("c", centre.city())
                    .param("p", centre.postcode()).param("country", centre.country()).param("lat", centre.lat())
                    .param("lon", centre.lon()).param("tz", centre.zone()).param("o", centre.org())
                    .param("company", centre.own() ? companyId : null)
                    .query(UUID.class).single();
            for (String assessor : centre.assessors()) {
                jdbc.sql("insert into partner_assessors (centre_id, full_name, email) values (:c, :n, :e)")
                        .param("c", id).param("n", assessor)
                        .param("e", assessor.toLowerCase().replace(' ', '.') + "@partners.example").update();
            }
            ZoneId zone = ZoneId.of(centre.zone());
            List<LocalTime> times = centre.zone().equals(london)
                    ? List.of(LocalTime.of(10, 0), LocalTime.of(13, 0), LocalTime.of(15, 30))
                    : List.of(LocalTime.of(10, 0), LocalTime.of(12, 30), LocalTime.of(15, 0));
            LocalDate today = LocalDate.now(clock.withZone(zone));
            for (int d = 1; d <= 21; d++) {
                LocalDate day = today.plusDays(d);
                if (day.getDayOfWeek().getValue() > 5) {
                    continue;
                }
                // A busy centre is fully booked for its first few days.
                int booked = d <= centre.busyDays() ? centre.seats() : 0;
                for (LocalTime time : times) {
                    Instant start = day.atTime(time).atZone(zone).toInstant();
                    jdbc.sql("insert into centre_slots (centre_id, start_at, end_at, seats, booked) values (:c, :s, :e, :seats, :b)")
                            .param("c", id).param("s", Timestamp.from(start))
                            .param("e", Timestamp.from(start.plus(Duration.ofHours(1)))).param("seats", centre.seats())
                            .param("b", booked).update();
                }
            }
        }
        // Earlier bookings in Madurai, oldest first, so the round robin has a history.
        int[] daysAgo = {5, 3, 1};
        List<String> madurai = List.of("AssessPoint Madurai – ELCOT IT Park", "TalentHub Madurai – K K Nagar",
                "SkillBridge Madurai – Anna Nagar");
        for (int i = 0; i < madurai.size(); i++) {
            jdbc.sql("""
                            update partner_centres set last_turn = nextval('centre_turn_seq'), last_assigned_at = :at
                             where name = :n
                            """)
                    .param("at", Timestamp.from(clock.instant().minus(Duration.ofDays(daysAgo[i]))))
                    .param("n", madurai.get(i)).update();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Demo only: moves a booked interview to a time booking wouldn't allow (less than an hour away). */
    private void moveStart(UUID interviewId, Instant start) {
        transaction.executeWithoutResult(status -> jdbc.sql("""
                        update interviews set start_at = :s, reply_by = least(reply_by, :s) where id = :id
                        """)
                .param("s", Timestamp.from(start)).param("id", interviewId).update());
    }

    /** About {@code minutes} from now, but still today (India time), so it shows under Today's interviews. */
    private Instant laterToday(int minutes) {
        Instant now = clock.instant();
        Instant wanted = now.plus(Duration.ofMinutes(minutes)).truncatedTo(ChronoUnit.MINUTES);
        wanted = wanted.minusSeconds(wanted.atZone(IST).getMinute() % 5 * 60L);
        if (!wanted.atZone(IST).toLocalDate().equals(now.atZone(IST).toLocalDate())) {
            wanted = now.plus(Duration.ofMinutes(Math.max(5, minutes / 10))).truncatedTo(ChronoUnit.MINUTES);
        }
        return wanted;
    }

    private Instant at(int daysAhead, LocalTime time) {
        LocalDate day = LocalDate.now(clock.withZone(IST)).plusDays(daysAhead);
        while (day.getDayOfWeek().getValue() > 5) {
            day = day.plusDays(1);
        }
        return day.atTime(time).atZone(IST).toInstant().truncatedTo(ChronoUnit.MINUTES);
    }

    private Job jobEntity(UUID jobId) {
        return transaction.execute(status -> jobRepository.findById(jobId).orElseThrow());
    }

    private HrUser user(Company company, String name, String handle, HrUserRole role, String hash) {
        return users.save(withPassword(new HrUser(company.getId(), name, handle + "@pellhamrowe.example", role), hash));
    }

    private void actAs(HrUser user, Runnable work) {
        HrUserPrincipal principal = new HrUserPrincipal(user.getId(), user.getCompanyId(), user.getFullName(),
                user.getWorkEmail(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, Authorities.forRole(user.getRole())));
        try {
            work.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static HrUser withPassword(HrUser user, String hash) {
        user.setPasswordHash(hash);
        return user;
    }

    private static List<Resource> resources(String pattern) {
        try {
            Resource[] found = new PathMatchingResourcePatternResolver().getResources(pattern);
            return new ArrayList<>(Arrays.stream(found).sorted(Comparator.comparing(Resource::getFilename)).toList());
        } catch (IOException ex) {
            throw new IllegalStateException("Demo CVs are missing", ex);
        }
    }

    // ------------------------------------------------------------------ job descriptions

    private static final String AWS_JD = """
            About the role
            Pellham & Rowe is growing its cloud platform team. You'll design, build and run secure AWS infrastructure for our financial services products, working with product engineers and security.

            What you'll do
            - Build and maintain AWS accounts, networks and services with Terraform
            - Own IAM policies and least-privilege access across teams
            - Run CI/CD pipelines that deploy safely many times a day
            - Monitor production with CloudWatch and help on call

            Requirements
            - 4+ years of hands-on AWS (EC2, S3, IAM, VPC)
            - Terraform for infrastructure as code
            - CI/CD with GitHub Actions, GitLab CI or Jenkins

            Nice to have
            - Kubernetes (EKS)
            - Python scripting
            - AWS Solutions Architect certification""";

    private static final String JAVA_JD = """
            About the role
            Join the team building our policy and payments services. You'll write Java and Spring Boot microservices used by thousands of customers every day.

            What you'll do
            - Build REST APIs and services in Java and Spring Boot
            - Design SQL schemas and write efficient queries
            - Write tests and review code with the team

            Requirements
            - 2+ years of Java
            - Spring Boot and REST APIs
            - SQL (PostgreSQL or MySQL)

            Nice to have
            - Kafka
            - Docker
            - Microservices experience""";

    private static final String DATA_JD = """
            About the role
            Help our operations and finance teams make better decisions with clear, reliable data.

            What you'll do
            - Build Power BI dashboards and reports
            - Write SQL to model and check data
            - Work with teams to understand what they need

            Requirements
            - 2+ years with SQL
            - Power BI
            - Advanced Excel

            Nice to have
            - Python
            - Tableau""";
}
