package com.darshan.circl.participation;

import com.darshan.circl.common.error.ApiException;
import com.darshan.circl.common.web.CurrentUser;
import com.darshan.circl.participation.dto.JoinRequest;
import com.darshan.circl.participation.dto.JoinResponse;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activities/{activityId}")
@Validated
public class ParticipationController {

    private final ParticipationService service;
    private final MeterRegistry metrics;

    public ParticipationController(ParticipationService service, MeterRegistry metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @PostMapping("/join")
    public ResponseEntity<JoinResponse> join(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID activityId,
                                             @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 80) String key,
                                             @Valid @RequestBody(required = false) JoinRequest body) {
        int partySize = body == null ? 1 : body.partySizeOrDefault();
        ParticipationService.JoinResult result;
        try {
            result = service.join(activityId, CurrentUser.id(jwt), partySize, key);
        } catch (ApiException e) {
            // e.g. activity full for the party, already started, too low a reliability score
            metrics.counter("circl.bookings", "outcome", "failed", "reason", e.getClass().getSimpleName()).increment();
            throw e;
        }
        // same tag names on every booking counter, Prometheus drops meters whose tag names differ
        metrics.counter("circl.bookings", "outcome", result.replayed() ? "replayed"
                : result.response().status().name().toLowerCase(), "reason", "none").increment();
        return ResponseEntity.ok()
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.response());
    }

    @DeleteMapping("/participants/me")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID activityId) {
        service.leave(activityId, CurrentUser.id(jwt));
        return ResponseEntity.noContent().build();
    }
}
