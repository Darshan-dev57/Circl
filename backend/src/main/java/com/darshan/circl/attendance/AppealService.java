package com.darshan.circl.attendance;

import com.darshan.circl.activity.Activity;
import com.darshan.circl.activity.ActivityRepository;
import com.darshan.circl.common.error.ConflictException;
import com.darshan.circl.common.error.ForbiddenException;
import com.darshan.circl.common.error.NotFoundException;
import com.darshan.circl.common.error.RuleViolationException;
import com.darshan.circl.common.text.TextSanitizer;
import com.darshan.circl.participation.Participant;
import com.darshan.circl.participation.ParticipantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AppealService {

    private final NoShowAppealRepository appeals;
    private final ParticipantRepository participants;
    private final ActivityRepository activities;
    private final Duration window;
    private final Clock clock;

    public AppealService(NoShowAppealRepository appeals, ParticipantRepository participants,
                         ActivityRepository activities,
                         @Value("${circl.attendance.appeal-window:PT48H}") Duration window, Clock clock) {
        this.appeals = appeals;
        this.participants = participants;
        this.activities = activities;
        this.window = window;
        this.clock = clock;
    }

    @Transactional
    public NoShowAppeal appeal(UUID participantId, UUID userId, String reason) {
        Instant now = Instant.now(clock);
        Participant p = participants.findById(participantId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Participation", participantId));
        if (p.getAttendanceStatus() != AttendanceStatus.NO_SHOW) {
            throw new RuleViolationException("not-a-no-show", "Only a no-show can be appealed");
        }
        Instant closes = p.getNoShowAt().plus(window);
        if (now.isAfter(closes)) {
            throw new RuleViolationException("appeal-window-closed", "The 48 hour appeal window has closed");
        }
        if (appeals.existsByParticipantId(participantId)) {
            throw new ConflictException("appeal-exists", "This no-show was already appealed");
        }
        return appeals.save(new NoShowAppeal(participantId, TextSanitizer.plainText(reason), closes, now));
    }

    @Transactional
    public NoShowAppeal decide(UUID appealId, UUID hostId, boolean accept, String note) {
        NoShowAppeal appeal = appeals.findById(appealId).orElseThrow(() -> new NotFoundException("Appeal", appealId));
        Participant p = participants.findById(appeal.getParticipantId()).orElseThrow();
        Activity activity = activities.findById(p.getActivityId()).orElseThrow();
        if (!activity.isHostedBy(hostId)) {
            throw new ForbiddenException("Only the host can decide this appeal");
        }
        if (appeal.getStatus() != NoShowAppeal.Status.OPEN) {
            throw new ConflictException("appeal-decided", "This appeal was already decided");
        }
        appeal.decide(accept, TextSanitizer.plainText(note), Instant.now(clock));
        return appeal;
    }
}
