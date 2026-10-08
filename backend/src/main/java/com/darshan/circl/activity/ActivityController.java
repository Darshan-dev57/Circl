package com.darshan.circl.activity;

import com.darshan.circl.activity.dto.ActivityDetail;
import com.darshan.circl.activity.dto.ActivitySummary;
import com.darshan.circl.activity.dto.CreateActivityRequest;
import com.darshan.circl.activity.dto.UpdateActivityRequest;
import com.darshan.circl.common.web.CurrentUser;
import com.darshan.circl.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
import java.util.List;
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
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public ResponseEntity<ActivityDetail> create(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody CreateActivityRequest request) {
        ActivityDetail created = service.create(CurrentUser.id(jwt), request);
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

    @GetMapping("/nearby")
    public List<ActivitySummary> nearby(@RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
                                        @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
                                        @RequestParam(defaultValue = "3") @DecimalMin("0.1") @DecimalMax("50.0") double radiusKm,
                                        @RequestParam(required = false) Category category,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.nearby(round3(lat), round3(lng), radiusKm, category, limit);
    }

    /** ~110 m; nearby results are the same for everyone in that cell, which makes them cacheable */
    private static double round3(double degrees) {
        return Math.round(degrees * 1000) / 1000.0;
    }

    @GetMapping("/{id}")
    public ActivityDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public ActivityDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @Valid @RequestBody UpdateActivityRequest request) {
        return service.update(id, CurrentUser.id(jwt), CurrentUser.isAdmin(jwt), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.cancel(id, CurrentUser.id(jwt), CurrentUser.isAdmin(jwt));
        return ResponseEntity.noContent().build();
    }
}
