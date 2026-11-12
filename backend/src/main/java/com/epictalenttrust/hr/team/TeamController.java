package com.epictalenttrust.hr.team;

import com.epictalenttrust.hr.security.AdminOnly;
import com.epictalenttrust.hr.team.TeamService.Change;
import com.epictalenttrust.hr.team.TeamService.Created;
import com.epictalenttrust.hr.team.TeamService.Member;
import com.epictalenttrust.hr.team.TeamService.NewMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Settings → Team: HR admins manage who can sign in. Everyone can see the list. */
@RestController
public class TeamController {

    public record AddRequest(@NotEmpty @Size(max = 25) List<@Valid NewMember> people) {
    }

    private final TeamService team;

    public TeamController(TeamService team) {
        this.team = team;
    }

    @GetMapping("/api/team")
    public List<Member> list() {
        return team.list();
    }

    @PostMapping("/api/team")
    @AdminOnly
    public List<Created> add(@Valid @RequestBody AddRequest body) {
        return team.add(body.people());
    }

    @PutMapping("/api/team/{userId}")
    @AdminOnly
    public Member change(@PathVariable UUID userId, @Valid @RequestBody Change body) {
        return team.change(userId, body);
    }

    @PostMapping("/api/team/{userId}/reset-password")
    @AdminOnly
    public Created resetPassword(@PathVariable UUID userId) {
        return team.resetPassword(userId);
    }

    @PostMapping("/api/team/{userId}/reset-two-step")
    @AdminOnly
    public Member resetTwoStep(@PathVariable UUID userId) {
        return team.resetTwoStep(userId);
    }
}
