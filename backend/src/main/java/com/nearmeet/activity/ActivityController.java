package com.nearmeet.activity;

import com.nearmeet.activity.dto.ActivityDetail;
import com.nearmeet.activity.dto.ActivitySummary;
import com.nearmeet.activity.dto.CreateActivityRequest;
import com.nearmeet.activity.dto.UpdateActivityRequest;
import com.nearmeet.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activities")
@Validated
public class ActivityController {

    private final ActivityService service;

    public ActivityController(ActivityService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ActivityDetail> create(@Valid @RequestBody CreateActivityRequest request) {
        ActivityDetail created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PageResponse<ActivitySummary> list(@RequestParam(required = false) Category category,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("startsAt").ascending().and(Sort.by("id")));
        return PageResponse.of(service.listUpcoming(category, pageable));
    }

    @GetMapping("/{id}")
    public ActivityDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public ActivityDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateActivityRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
