package com.epictalenttrust.hr.cover;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** "Going on leave": hand your jobs to a colleague for set dates. */
@RestController
@RequestMapping("/api/leave-cover")
public class LeaveCoverController {

    private final LeaveCoverService covers;

    public LeaveCoverController(LeaveCoverService covers) {
        this.covers = covers;
    }

    @GetMapping
    public List<LeaveCoverService.CoverView> list() {
        return covers.list();
    }

    @GetMapping("/colleagues")
    public List<LeaveCoverService.Person> colleagues() {
        return covers.colleagues();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveCoverService.CoverView create(@Valid @RequestBody LeaveCoverService.CoverInput body) {
        return covers.create(body);
    }

    @PutMapping("/{id}")
    public LeaveCoverService.CoverView changeDates(@PathVariable UUID id, @Valid @RequestBody LeaveCoverService.DatesInput body) {
        return covers.changeDates(id, body);
    }

    @PostMapping("/{id}/end")
    public LeaveCoverService.CoverView end(@PathVariable UUID id) {
        return covers.end(id);
    }
}
