package com.epictalenttrust.hr.message;

import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.job.InterviewMode;
import com.epictalenttrust.hr.job.RoundKind;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wording of every email the portal sends. Each email is a template with {{placeholders}}; HR admins can change the
 * wording for their company under Settings, Email templates, and the portal's own wording is used otherwise.
 */
@Component
public class MessageTemplates {

    public record Content(String subject, String body) {
    }

    /** What an interview email needs to say. {@code candidatePage} is the candidate's own page link, if any. */
    public record InterviewFacts(UUID companyId, String candidateFirstName, String jobTitle, String companyName, int round,
                                 String roundName, RoundKind kind, InterviewMode mode, Instant startAt,
                                 ZoneId candidateZone, int durationMin, Instant replyBy, String candidatePage) {

        public InterviewFacts withCandidatePage(String link) {
            return new InterviewFacts(companyId, candidateFirstName, jobTitle, companyName, round, roundName, kind, mode,
                    startAt, candidateZone, durationMin, replyBy, link);
        }

        /** "Technical Level 1 (Round 2)". */
        public String roundLabel() {
            return label(roundName, round);
        }
    }

    /** One email the company can reword: the placeholders it may use, and the portal's own wording. */
    public record Definition(String key, String label, String description, List<String> placeholders,
                             String subject, String body) {
    }

    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm z", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm z", Locale.ENGLISH);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z]+)\\s*}}");
    private static final String SIGN = "Kind regards,\nThe ETT team on behalf of {{company}}";
    private static final String REPLY = """
            {{candidatePage}}

            If the time doesn't work, ask for another time and tell us the days you're free. You don't lose your place by asking.""";

    public static final Map<String, Definition> DEFINITIONS = definitions();

    private final JdbcClient jdbc;

    public MessageTemplates(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ candidate emails

    /** An AI interview, online. */
    public Content invitation(InterviewFacts f) {
        return render(f.companyId(), "invitation_ai", vars(f));
    }

    /** A partner assessor round, in person at a partner centre. */
    public Content invitationAtCentre(InterviewFacts f, String centreName, String address, Instant arriveBy) {
        Map<String, String> v = vars(f);
        v.put("arriveBy", TIME.format(arriveBy.atZone(f.candidateZone())));
        v.put("centre", centreName);
        v.put("address", address == null ? "" : address);
        return render(f.companyId(), "invitation_partner", v);
    }

    /** A panel interview: online with a meeting link, or in person at a venue. */
    public Content invitationWithPanel(InterviewFacts f, String meetingLink, String venue) {
        Map<String, String> v = vars(f);
        v.put("where", meetingLink != null ? "Join here: " + meetingLink : "Where: " + venue);
        v.put("format", meetingLink != null ? "Online video call with the interview panel" : "In person with the interview panel");
        return render(f.companyId(), "invitation_panel", v);
    }

    public Content newTime(InterviewFacts f, String personalNote) {
        Map<String, String> v = vars(f);
        v.put("note", note(personalNote));
        return render(f.companyId(), "new_time", v);
    }

    public Content keptTime(InterviewFacts f, String personalNote) {
        Map<String, String> v = vars(f);
        v.put("note", note(personalNote));
        return render(f.companyId(), "kept_time", v);
    }

    public Content reminder(InterviewFacts f) {
        return render(f.companyId(), "reminder", vars(f));
    }

    public Content cancelled(InterviewFacts f, String reason) {
        Map<String, String> v = vars(f);
        v.put("note", note(reason));
        return render(f.companyId(), "cancelled", v);
    }

    /** The job closed, so the candidate's upcoming interview is cancelled. */
    public Content jobClosedInterview(InterviewFacts f) {
        return render(f.companyId(), "job_closed_interview", vars(f));
    }

    /** The job closed while the candidate was part-way through. */
    public Content positionClosed(UUID companyId, String firstName, String jobTitle, String companyName) {
        return render(companyId, "position_closed", base(firstName, jobTitle, companyName));
    }

    public Content progressed(UUID companyId, String firstName, String jobTitle, String companyName, int nextRound,
                              String nextRoundName) {
        Map<String, String> v = base(firstName, jobTitle, companyName);
        v.put("nextRound", label(nextRoundName, nextRound));
        return render(companyId, "progressed", v);
    }

    public Content offer(UUID companyId, String firstName, String jobTitle, String companyName) {
        return render(companyId, "offer", base(firstName, jobTitle, companyName));
    }

    public Content notProgressing(UUID companyId, String firstName, String jobTitle, String companyName, String reason) {
        Map<String, String> v = base(firstName, jobTitle, companyName);
        v.put("reason", reason);
        return render(companyId, "not_progressing", v);
    }

    // ------------------------------------------------------------------ panel emails

    /** To one panel member: the interview, their code and where to sign in. */
    public Content panelInvitation(InterviewFacts f, String memberName, String candidateName, String where, String code,
                                   String signIn, ZoneId memberZone) {
        Map<String, String> v = base(memberName == null ? "there" : memberName.strip().split("\\s+")[0], f.jobTitle(),
                f.companyName());
        v.put("candidate", candidateName);
        v.put("round", f.roundLabel());
        v.put("when", DAY_TIME.format(f.startAt().atZone(memberZone)));
        v.put("length", length(f.durationMin()));
        v.put("where", where);
        v.put("code", code);
        v.put("signIn", signIn);
        return render(f.companyId(), "panel_invitation", v);
    }

    /** To one panel member: the interview moved or was cancelled. */
    public Content panelUpdate(InterviewFacts f, String memberName, String candidateName, boolean cancelled,
                               ZoneId memberZone) {
        String first = memberName == null ? "there" : memberName.strip().split("\\s+")[0];
        String subject = (cancelled ? "Cancelled: " : "New time: ") + f.roundLabel() + " with " + candidateName;
        String body = cancelled
                ? """
                Hello %s,

                The %s interview with %s for the %s role has been cancelled. You don't need to do anything.

                Thank you,
                %s""".formatted(first, f.roundLabel(), candidateName, f.jobTitle(), f.companyName())
                : """
                Hello %s,

                The %s interview with %s for the %s role has a new time.

                When: %s
                Length: %s

                Your interview code stays the same.

                Thank you,
                %s""".formatted(first, f.roundLabel(), candidateName, f.jobTitle(),
                DAY_TIME.format(f.startAt().atZone(memberZone)), length(f.durationMin()), f.companyName());
        return new Content(subject, body);
    }

    // ------------------------------------------------------------------ rendering

    /** The wording the company uses: their own if they changed it, otherwise the portal's. */
    public Content render(UUID companyId, String key, Map<String, String> vars) {
        Definition definition = DEFINITIONS.get(key);
        String subject = definition.subject();
        String body = definition.body();
        if (companyId != null) {
            var custom = jdbc.sql("select subject, body from email_templates where company_id = :c and template_key = :k")
                    .param("c", companyId).param("k", key)
                    .query((rs, n) -> new Content(rs.getString("subject"), rs.getString("body"))).optional();
            if (custom.isPresent()) {
                subject = custom.get().subject();
                body = custom.get().body();
            }
        }
        return new Content(fill(subject, vars).replaceAll("\\s+", " ").strip(), tidy(fill(body, vars)));
    }

    /** Fills {{placeholders}}; unknown ones are left out. */
    public static String fill(String text, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = vars.getOrDefault(m.group(1), "");
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Placeholders a template uses that the email doesn't offer. */
    public static List<String> unknownPlaceholders(String key, String text) {
        Definition definition = DEFINITIONS.get(key);
        Matcher m = PLACEHOLDER.matcher(text == null ? "" : text);
        java.util.List<String> unknown = new java.util.ArrayList<>();
        while (m.find()) {
            if (!definition.placeholders().contains(m.group(1)) && !unknown.contains(m.group(1))) {
                unknown.add(m.group(1));
            }
        }
        return unknown;
    }

    private static String tidy(String body) {
        return body.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
    }

    private Map<String, String> vars(InterviewFacts f) {
        Map<String, String> v = base(f.candidateFirstName(), f.jobTitle(), f.companyName());
        v.put("round", f.roundLabel());
        v.put("when", when(f.startAt(), f.candidateZone()));
        v.put("length", length(f.durationMin()));
        v.put("replyBy", DAY_TIME.format(f.replyBy().atZone(f.candidateZone())));
        v.put("format", format(f.kind(), f.mode()));
        v.put("candidatePage", f.candidatePage() == null
                ? "Please reply to this email by " + DAY_TIME.format(f.replyBy().atZone(f.candidateZone()))
                        + " to accept this time."
                : "Please accept this time by " + DAY_TIME.format(f.replyBy().atZone(f.candidateZone()))
                        + ". You can accept, ask for another time or send us a message on your page:\n" + f.candidatePage());
        return v;
    }

    private static Map<String, String> base(String firstName, String jobTitle, String companyName) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("firstName", firstName);
        v.put("jobTitle", jobTitle);
        v.put("company", companyName);
        return v;
    }

    public static String label(String roundName, int round) {
        return roundName == null || roundName.isBlank() ? "Round " + round : roundName + " (Round " + round + ")";
    }

    static String when(Instant start, ZoneId candidateZone) {
        ZoneId other = TimeZones.otherZone(candidateZone);
        return DAY_TIME.format(start.atZone(candidateZone)) + " (" + TIME.format(start.atZone(other)) + ")";
    }

    static String length(int minutes) {
        if (minutes < 60) {
            return minutes + " minutes";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        return h + (h == 1 ? " hour" : " hours") + (m == 0 ? "" : " " + m + " minutes");
    }

    private static String format(RoundKind kind, InterviewMode mode) {
        if (kind == RoundKind.AI_INTERVIEW) {
            return "Online interview led by our AI interviewer. You can speak or type your answers, and a person reviews every score.";
        }
        if (kind == RoundKind.PARTNER) {
            return "In person at a partner assessment centre";
        }
        return switch (mode == null ? InterviewMode.ONLINE : mode) {
            case IN_PERSON -> "In person with the interview panel";
            case PHONE -> "Phone call";
            case ONLINE -> "Online video call with the interview panel";
        };
    }

    private static String note(String text) {
        return text == null || text.isBlank() ? "" : text.strip();
    }

    /** A ready-made message HR can start from when writing to a candidate. */
    public record Template(String key, String label, String subject, String body) {
    }

    public List<Template> hrTemplates(String firstName, String jobTitle, String companyName, String hrName) {
        String sign = "\n\nKind regards,\n" + hrName + "\n" + companyName;
        return List.of(
                new Template("general", "General update", "Your application for " + jobTitle,
                        "Dear " + firstName + ",\n\n" + sign.strip()),
                new Template("request_details", "Ask for notice period and CTC",
                        "A few details for your " + jobTitle + " application",
                        "Dear " + firstName + ",\n\nThank you for your interest in the " + jobTitle + " role. To help us move "
                                + "your application forward, could you reply with:\n\n"
                                + "1. Your notice period (and last working day, if you're serving notice)\n"
                                + "2. Your current CTC\n3. Your expected CTC\n4. Your current and preferred locations" + sign),
                new Template("request_cv", "Ask for an updated CV", "Your latest CV",
                        "Dear " + firstName + ",\n\nCould you send us your latest CV as a PDF or Word file? "
                                + "We'd like to make sure we have your most recent experience for the " + jobTitle + " role." + sign),
                new Template("on_hold", "Application on hold", "Your application for " + jobTitle,
                        "Dear " + firstName + ",\n\nThank you for your patience. Your application for the " + jobTitle
                                + " role is on hold for now while we review other candidates. We'll be in touch as soon as "
                                + "we have an update." + sign),
                new Template("centre_documents", "Documents for an in-person interview",
                        "What to bring to your in-person interview",
                        "Dear " + firstName + ",\n\nFor your in-person interview, please bring your original Aadhaar card "
                                + "and PAN card. The assessor will check them before the interview starts. Please arrive "
                                + "15 minutes early." + sign),
                new Template("thank_you", "Thank you", "Thank you, " + firstName,
                        "Dear " + firstName + ",\n\nThank you for the time you've given to our process for the " + jobTitle
                                + " role so far. We'll be in touch with next steps soon." + sign));
    }

    private static Map<String, Definition> definitions() {
        List<String> interview = List.of("firstName", "jobTitle", "company", "round", "when", "length", "replyBy",
                "format", "candidatePage");
        Map<String, Definition> d = new LinkedHashMap<>();
        d.put("invitation_ai", new Definition("invitation_ai", "Invitation: AI interview",
                "Sent when an AI interview round is booked.", interview,
                "Interview invitation: {{round}}, {{jobTitle}}",
                """
                Dear {{firstName}},

                Thank you for applying for the {{jobTitle}} role with {{company}}. We'd like to invite you to an interview: {{round}}.

                When: {{when}}
                Length: {{length}}
                Format: {{format}}

                What you'll need:
                - Your Aadhaar card and PAN card. The AI interviewer will ask you to show them to the camera.
                - A laptop or phone with a camera and microphone.
                - A quiet, private room. You'll be asked to show the room on camera before the interview starts.

                """ + REPLY + """


                If you need any adjustments, such as extra time or captions, tell us.

                """ + SIGN));
        List<String> partner = new java.util.ArrayList<>(interview);
        partner.addAll(List.of("arriveBy", "centre", "address"));
        d.put("invitation_partner", new Definition("invitation_partner", "Invitation: partner assessment centre",
                "Sent when a partner assessment centre round is booked.", partner,
                "Interview invitation: {{round}}, {{jobTitle}} (in person)",
                """
                Dear {{firstName}},

                Good news: we'd like to invite you to an in-person interview ({{round}}) for the {{jobTitle}} role with {{company}}.

                When: {{when}}
                Please arrive by: {{arriveBy}}
                Where: {{centre}}, {{address}}
                Length: {{length}}

                Please bring your original Aadhaar card and PAN card. The assessor will check them before the interview starts.

                """ + REPLY + """


                If you need any adjustments, such as step-free access or extra time, tell us.

                """ + SIGN));
        List<String> panel = new java.util.ArrayList<>(interview);
        panel.add("where");
        d.put("invitation_panel", new Definition("invitation_panel", "Invitation: panel interview",
                "Sent when a panel interview round is booked.", panel,
                "Interview invitation: {{round}}, {{jobTitle}}",
                """
                Dear {{firstName}},

                We'd like to invite you to an interview for the {{jobTitle}} role with {{company}}: {{round}}.

                When: {{when}}
                Length: {{length}}
                Format: {{format}}
                {{where}}

                """ + REPLY + """


                """ + SIGN));
        List<String> withNote = new java.util.ArrayList<>(interview);
        withNote.add("note");
        d.put("new_time", new Definition("new_time", "New interview time",
                "Sent when HR moves an interview to a new time.", withNote,
                "New interview time: {{round}}, {{jobTitle}}",
                """
                Dear {{firstName}},

                Your interview for {{jobTitle}} ({{round}}) has a new time.

                When: {{when}}
                Length: {{length}}

                {{note}}

                {{candidatePage}}

                """ + SIGN));
        d.put("kept_time", new Definition("kept_time", "Time kept",
                "Sent when HR can't move an interview and keeps the original time.", withNote,
                "About your interview time: {{round}}, {{jobTitle}}",
                """
                Dear {{firstName}},

                Thanks for asking about a different time. We aren't able to move your interview ({{round}}), so the original time stays the same.

                When: {{when}}

                {{note}}

                Please let us know if you can't attend, and we'll talk about what we can do.

                """ + SIGN));
        d.put("reminder", new Definition("reminder", "Reply reminder",
                "Sent when a candidate hasn't replied and the deadline is less than a day away.", interview,
                "Reminder: please reply about your interview ({{round}})",
                """
                Dear {{firstName}},

                This is a reminder about your interview for {{jobTitle}} ({{round}}).

                When: {{when}}

                {{candidatePage}}

                """ + SIGN));
        d.put("cancelled", new Definition("cancelled", "Interview cancelled",
                "Sent when HR cancels an interview.", withNote,
                "Your interview for {{jobTitle}} ({{round}}) is cancelled",
                """
                Dear {{firstName}},

                We're sorry, but your interview ({{round}}) on {{when}} has been cancelled.

                {{note}}

                We'll be in touch about what happens next.

                """ + SIGN));
        d.put("job_closed_interview", new Definition("job_closed_interview", "Interview cancelled: position closed",
                "Sent automatically when a job is closed, to each candidate with an upcoming interview.", interview,
                "Your interview for {{jobTitle}} is cancelled",
                """
                Dear {{firstName}},

                We're sorry to let you know that the {{jobTitle}} role with {{company}} has now closed, so your interview ({{round}}) on {{when}} is cancelled. You don't need to do anything.

                Thank you for your time and interest in the role. We wish you every success.

                """ + SIGN));
        d.put("position_closed", new Definition("position_closed", "Position closed",
                "Sent automatically when a job is closed, to candidates part-way through with no interview booked.",
                List.of("firstName", "jobTitle", "company"),
                "Update on the {{jobTitle}} role",
                """
                Dear {{firstName}},

                Thank you for your interest in the {{jobTitle}} role with {{company}}. We're writing to let you know that the position has now closed, so we won't be taking your application further.

                We're grateful for the time you've put into the process, and we wish you every success.

                """ + SIGN));
        d.put("progressed", new Definition("progressed", "Through to the next round",
                "Sent when HR moves a candidate on to the next round.", List.of("firstName", "jobTitle", "company", "nextRound"),
                "You're through to {{nextRound}}: {{jobTitle}}",
                """
                Dear {{firstName}},

                Good news: you're through to {{nextRound}} for the {{jobTitle}} role with {{company}}.

                We'll email you the time for your next interview soon.

                """ + SIGN));
        d.put("not_progressing", new Definition("not_progressing", "Not progressing",
                "Sent when HR decides not to progress a candidate after a round.",
                List.of("firstName", "jobTitle", "company", "reason"),
                "Your application for {{jobTitle}}",
                """
                Dear {{firstName}},

                Thank you for the time you put into your application for the {{jobTitle}} role with {{company}}. After reviewing your interview, the hiring team has decided not to progress your application.

                Reason: {{reason}}

                If you think something went wrong, for example a technical problem during your interview, reply to this email within 14 days and a person will review the decision.

                We wish you every success.

                """ + SIGN));
        d.put("offer", new Definition("offer", "Offer", "Sent when the last round is passed.",
                List.of("firstName", "jobTitle", "company"),
                "Good news about the {{jobTitle}} role",
                """
                Dear {{firstName}},

                We're delighted to tell you that {{company}} would like to offer you the {{jobTitle}} role. The hiring team will contact you shortly with the details of the offer.

                Congratulations, and thank you for the time you've put into the process.

                """ + SIGN));
        d.put("panel_invitation", new Definition("panel_invitation", "Panel member: interview code",
                "Sent to each panel member when a panel interview is booked.",
                List.of("firstName", "jobTitle", "company", "candidate", "round", "when", "length", "where", "code", "signIn"),
                "Interview panel: {{round}} with {{candidate}}",
                """
                Hello {{firstName}},

                You're on the interview panel for {{candidate}}, {{round}}, for the {{jobTitle}} role.

                When: {{when}}
                Length: {{length}}
                {{where}}

                Your interview code: {{code}}

                After the interview, please send your review:
                1. Go to {{signIn}} and sign in with the panel login.
                2. Enter your employee ID and the interview code above.
                3. Fill in the review form and send it.

                The code works from an hour before the interview, so you can read the CV first.

                Thank you,
                {{company}}"""));
        return java.util.Collections.unmodifiableMap(d);
    }
}
