package com.darshan.circl.attendance;

import com.darshan.circl.common.web.CurrentUser;
import com.darshan.circl.participation.Participant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/activities/{activityId}")
public class AttendanceController {

    public record CheckinRequest(@NotBlank String code) {
    }

    public record EvidenceRequest(@Size(max = 500) String note, boolean markAttended) {
    }

    public record AttendanceResponse(UUID participantId, UUID userId, AttendanceStatus attendanceStatus, boolean late,
                                     Instant checkedInAt, String hostEvidence) {
        static AttendanceResponse of(Participant p) {
            return new AttendanceResponse(p.getId(), p.getUserId(), p.getAttendanceStatus(), p.isLate(),
                    p.getCheckedInAt(), p.getHostEvidence());
        }
    }

    public record CodeResponse(String code, Instant expiresAt) {
    }

    private final AttendanceService attendance;

    public AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @PostMapping("/confirm")
    public AttendanceStatus confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID activityId) {
        return attendance.confirm(activityId, CurrentUser.id(jwt));
    }

    @GetMapping("/checkin-code")
    public CodeResponse checkinCode(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID activityId) {
        CheckinTokens.Issued issued = attendance.checkinCode(activityId, CurrentUser.id(jwt));
        return new CodeResponse(issued.token(), issued.expiresAt());
    }

    @PostMapping("/checkin")
    public AttendanceResponse checkIn(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID activityId,
                                      @Valid @RequestBody CheckinRequest body) {
        return AttendanceResponse.of(attendance.checkIn(activityId, CurrentUser.id(jwt), body.code()));
    }

    @PostMapping("/attendance/{userId}/evidence")
    public AttendanceResponse evidence(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID activityId,
                                       @PathVariable UUID userId, @Valid @RequestBody EvidenceRequest body) {
        return AttendanceResponse.of(attendance.hostEvidence(activityId, CurrentUser.id(jwt), userId,
                body.note(), body.markAttended()));
    }
}
