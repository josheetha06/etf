package com.epictalenttrust.hr;

import com.epictalenttrust.hr.security.TotpService;
import com.epictalenttrust.hr.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An HR admin creates logins; new people choose their own password; access can be turned off. */
class TeamLoginsIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42!";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TotpService totp;

    @Test
    void adminCreatesLoginsAndNewPeopleChooseTheirOwnPassword() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        UUID company = jdbc.queryForObject("insert into companies (name, country, data_region) values (?, 'India', 'apac'::data_region_t) returning id",
                UUID.class, "Team " + run);
        String adminEmail = "admin-" + run + "@team.example";
        String recruiterEmail = "recruiter-" + run + "@team.example";
        seedUser(company, adminEmail, "admin");
        seedUser(company, recruiterEmail, "recruiter");
        MockHttpSession admin = signIn(adminEmail, PASSWORD);

        // Two people at once; each gets a one-time password, shown once.
        String newEmail = "asha-" + run + "@team.example";
        MvcResult created = mvc.perform(post("/api/team").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("people", List.of(
                                Map.of("fullName", "Asha Kumar", "email", newEmail, "role", "RECRUITER"),
                                Map.of("fullName", "Panel login", "email", "panel-" + run + "@team.example", "role", "PANEL"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        String oneTime = JsonPath.read(created.getResponse().getContentAsString(), "$[0].oneTimePassword");
        String ashaId = JsonPath.read(created.getResponse().getContentAsString(), "$[0].id");

        // The Interviewer role is retired: panels use the shared panel login.
        mvc.perform(post("/api/team").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("people", List.of(Map.of("fullName", "Dev Patel", "email", "dev-" + run + "@team.example", "role", "INTERVIEWER"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("role_retired"));

        // The same email can't be added twice, and only admins can add people.
        mvc.perform(post("/api/team").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("people", List.of(Map.of("fullName", "Again", "email", newEmail.toUpperCase(), "role", "RECRUITER"))))))
                .andExpect(status().isConflict());
        MockHttpSession recruiter = signIn(recruiterEmail, PASSWORD);
        mvc.perform(post("/api/team").session(recruiter).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("people", List.of(Map.of("fullName", "X", "email", "x-" + run + "@team.example", "role", "ADMIN"))))))
                .andExpect(status().isForbidden());

        // First sign-in: two-step setup, then a new password before anything else.
        MockHttpSession asha = signIn(newEmail, oneTime);
        mvc.perform(get("/api/auth/me").session(asha)).andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/api/jobs").session(asha))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("password_change_required"));
        mvc.perform(post("/api/auth/password").session(asha).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newPassword", "short"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/password").session(asha).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newPassword", "a-long-sentence-asha-likes"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        mvc.perform(get("/api/jobs").session(asha)).andExpect(status().isOk());

        // Turning access off signs the person out at once; the last admin can't be removed.
        mvc.perform(put("/api/team/{id}", ashaId).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(get("/api/jobs").session(asha)).andExpect(status().isUnauthorized());
        String adminId = jdbc.queryForObject("select id::text from hr_users where work_email = ?", String.class, adminEmail);
        mvc.perform(put("/api/team/{id}", adminId).session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("active", false))))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult step = mvc.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        String secret = JsonPath.read(step.getResponse().getContentAsString(), "$.secret");
        mvc.perform(post("/api/auth/totp").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", totp.currentCode(secret)))))
                .andExpect(status().isOk());
        return session;
    }

    private void seedUser(UUID companyId, String email, String role) {
        String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD);
        jdbc.update("""
                insert into hr_users (company_id, full_name, work_email, password_hash, role, status)
                values (?, ?, ?, ?, ?::hr_user_role_t, 'active')""", companyId, "Test " + role, email, hash, role);
    }

    private static String json(Object value) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(value);
    }
}
