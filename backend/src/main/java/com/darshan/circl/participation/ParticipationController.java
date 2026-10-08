package com.darshan.circl.participation;

import com.darshan.circl.common.web.CurrentUser;
import com.darshan.circl.participation.dto.JoinResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activities/{activityId}")
@Validated
public class ParticipationController {

    private final ParticipationService service;

    public ParticipationController(ParticipationService service) {
        this.service = service;
    }

    @PostMapping("/join")
    public ResponseEntity<JoinResponse> join(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID activityId,
                                             @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 80) String key) {
        ParticipationService.JoinResult result = service.join(activityId, CurrentUser.id(jwt), key);
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
