package com.darshan.circl.attendance;

import com.darshan.circl.common.web.CurrentUser;
import com.darshan.circl.reliability.ReliabilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AppealController {

    public record AppealRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public enum Decision { ACCEPT, REJECT }

    public record DecisionRequest(@NotNull Decision decision, @Size(max = 500) String note) {
    }

    public record AppealResponse(UUID id, UUID participantId, NoShowAppeal.Status status, String reason,
                                 Instant appealClosesAt, Instant decidedAt) {
        static AppealResponse of(NoShowAppeal a) {
            return new AppealResponse(a.getId(), a.getParticipantId(), a.getStatus(), a.getUserReason(),
                    a.getAppealClosesAt(), a.getDecidedAt());
        }
    }

    private final AppealService appeals;
    private final ReliabilityService reliability;

    public AppealController(AppealService appeals, ReliabilityService reliability) {
        this.appeals = appeals;
        this.reliability = reliability;
    }

    @PostMapping("/me/attendance/{participantId}/appeal")
    public ResponseEntity<AppealResponse> appeal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId,
                                                 @Valid @RequestBody AppealRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppealResponse.of(appeals.appeal(participantId, CurrentUser.id(jwt), body.reason())));
    }

    @PostMapping("/appeals/{appealId}/decision")
    public AppealResponse decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID appealId,
                                 @Valid @RequestBody DecisionRequest body) {
        return AppealResponse.of(appeals.decide(appealId, CurrentUser.id(jwt), body.decision() == Decision.ACCEPT, body.note()));
    }

    @GetMapping("/me/reliability")
    public ReliabilityService.Breakdown myReliability(@AuthenticationPrincipal Jwt jwt) {
        return reliability.breakdown(CurrentUser.id(jwt));
    }
}
