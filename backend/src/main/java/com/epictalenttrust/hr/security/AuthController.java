package com.epictalenttrust.hr.security;

import com.epictalenttrust.hr.company.Company;
import com.epictalenttrust.hr.company.CompanyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final CompanyRepository companies;
    private final com.epictalenttrust.hr.hruser.HrUserRepository users;

    public AuthController(AuthService auth, CompanyRepository companies, com.epictalenttrust.hr.hruser.HrUserRepository users) {
        this.auth = auth;
        this.companies = companies;
        this.users = users;
    }

    public record PasswordRequest(@NotBlank @Email String email, @NotBlank @Size(max = 200) String password) {
    }

    public record CodeRequest(@NotBlank String code) {
    }

    public record Me(UUID id, String fullName, String email, String role, boolean canManageHiring, CompanyInfo company,
                     boolean mustChangePassword, boolean canDecide, boolean seesAllJobs) {
    }

    public record PasswordChange(@Size(max = 200) String currentPassword, @NotBlank @Size(max = 200) String newPassword) {
    }

    public record CompanyInfo(UUID id, String name, String country) {
    }

    /** Sets the XSRF-TOKEN cookie the frontend sends back on every change. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }

    @PostMapping("/login")
    public AuthService.PasswordStepResult login(@Valid @RequestBody PasswordRequest body, HttpServletRequest request,
                                                HttpServletResponse response) {
        return auth.checkPassword(body.email(), body.password(), request, response);
    }

    @PostMapping("/totp")
    public Me verifyCode(@Valid @RequestBody CodeRequest body, HttpServletRequest request, HttpServletResponse response) {
        return toMe(auth.checkCode(body.code(), request, response));
    }

    @PostMapping("/password")
    public Me changePassword(@Valid @RequestBody PasswordChange body, HttpServletRequest request) {
        auth.changePassword(body.currentPassword(), body.newPassword(), request);
        return toMe(CurrentUser.get());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        auth.signOut(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public Me me() {
        return toMe(CurrentUser.get());
    }

    private Me toMe(HrUserPrincipal user) {
        Company company = companies.findById(user.companyId()).orElseThrow();
        boolean mustChange = users.findById(user.id()).map(u -> u.isMustChangePassword()).orElse(false);
        return new Me(user.id(), user.fullName(), user.email(), user.role().name().toLowerCase(),
                user.role().canManageHiring(),
                new CompanyInfo(company.getId(), company.getName(), company.getCountry()), mustChange,
                user.role().canDecide(), user.role().seesAllJobs());
    }
}
