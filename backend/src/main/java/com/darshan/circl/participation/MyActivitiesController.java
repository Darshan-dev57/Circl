package com.darshan.circl.participation;

import com.darshan.circl.common.web.CurrentUser;
import com.darshan.circl.participation.dto.MyActivitiesPage;
import com.darshan.circl.participation.dto.ParticipantView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
public class MyActivitiesController {

    private final MyActivitiesService service;

    public MyActivitiesController(MyActivitiesService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/me/activities")
    public MyActivitiesPage mine(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(defaultValue = "upcoming") @Pattern(regexp = "upcoming|past") String when,
                                 @RequestParam(required = false) String cursor,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.mine(CurrentUser.id(jwt), "past".equals(when), cursor, size);
    }

    @GetMapping("/api/v1/activities/{activityId}/participants")
    public List<ParticipantView> participants(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID activityId) {
        return service.participantsOf(activityId, CurrentUser.id(jwt));
    }
}
