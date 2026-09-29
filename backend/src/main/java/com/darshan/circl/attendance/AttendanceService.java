package com.darshan.circl.attendance;

import com.darshan.circl.activity.Activity;
import com.darshan.circl.activity.ActivityRepository;
import com.darshan.circl.common.error.ForbiddenException;
import com.darshan.circl.common.error.NotFoundException;
import com.darshan.circl.common.error.RuleViolationException;
import com.darshan.circl.common.outbox.Outbox;
import com.darshan.circl.common.text.TextSanitizer;
import com.darshan.circl.participation.Participant;
import com.darshan.circl.participation.ParticipantRepository;
import com.darshan.circl.participation.ParticipantStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AttendanceService {

    private final ActivityRepository activities;
    private final ParticipantRepository participants;
    private final AttendanceLog log;
    private final CheckinTokens tokens;
    private final Outbox outbox;
    private final Duration opensBefore;
    private final Duration grace;
    private final Duration scoresFinalAfterEnd;
    private final Clock clock;

    public AttendanceService(ActivityRepository activities, ParticipantRepository participants, AttendanceLog log,
                             CheckinTokens tokens, Outbox outbox,
                             @Value("${circl.checkin.opens-before:PT30M}") Duration opensBefore,
                             @Value("${circl.checkin.grace-after-start:PT15M}") Duration grace,
                             @Value("${circl.attendance.finalize-after-end:PT2H}") Duration finalizeAfterEnd,
                             @Value("${circl.attendance.appeal-window:PT48H}") Duration appealWindow,
                             Clock clock) {
        this.activities = activities;
        this.participants = participants;
        this.log = log;
        this.tokens = tokens;
        this.outbox = outbox;
        this.opensBefore = opensBefore;
        this.grace = grace;
        this.scoresFinalAfterEnd = finalizeAfterEnd.plus(appealWindow);
        this.clock = clock;
    }

    /** "I'll be there" */
    @Transactional
    public AttendanceStatus confirm(UUID activityId, UUID userId) {
        Activity activity = activity(activityId);
        if (!activity.getStartsAt().isAfter(Instant.now(clock))) {
            throw new RuleViolationException("activity-started", "The activity has already started");
        }
        Participant p = joinedParticipant(activityId, userId);
        if (p.getAttendanceStatus() == AttendanceStatus.RECONFIRMED) {
            return AttendanceStatus.RECONFIRMED;
        }
        AttendanceStatus from = p.moveTo(AttendanceStatus.RECONFIRMED);
        log.record(p.getId(), from, AttendanceStatus.RECONFIRMED, Actor.USER, null);
        return AttendanceStatus.RECONFIRMED;
    }

    @Transactional(readOnly = true)
    public CheckinTokens.Issued checkinCode(UUID activityId, UUID hostId) {
        Activity activity = activity(activityId);
        requireHost(activity, hostId);
        requireOpen(activity);
        requireCheckinWindow(activity, Instant.now(clock));
        return tokens.issue(activityId);
    }

    @Transactional
    public Participant checkIn(UUID activityId, UUID userId, String code) {
        Instant now = Instant.now(clock);
        Activity activity = activity(activityId);
        requireOpen(activity);
        if (!tokens.isValid(code, activityId)) {
            throw new RuleViolationException("invalid-checkin-code", "Check-in code is invalid or expired");
        }
        requireCheckinWindow(activity, now);
        Participant p = participants.findByActivityIdAndUserId(activityId, userId)
                .filter(Participant::isJoined)
                .orElseThrow(() -> new ForbiddenException("Only participants of this activity can check in"));
        boolean late = now.isAfter(activity.getStartsAt());
        AttendanceStatus from = p.getAttendanceStatus();
        p.markCheckedIn(now, late);
        log.record(p.getId(), from, AttendanceStatus.CHECKED_IN, Actor.USER, late ? "late" : null);
        outbox.append(activityId, "ParticipantCheckedIn", Map.of("activityId", activityId, "userId", userId, "late", late));
        return p;
    }

    /**
     * Host adds a note to someone's attendance, and can mark them present when the scan failed:
     * before finalizing that is a manual check-in, after it a NO_SHOW becomes ATTENDED.
     */
    @Transactional
    public Participant hostEvidence(UUID activityId, UUID hostId, UUID userId, String note, boolean markAttended) {
        Activity activity = activity(activityId);
        requireHost(activity, hostId);
        Participant p = participants.findByActivityIdAndUserId(activityId, userId)
                .filter(x -> x.getStatus() == ParticipantStatus.JOINED)
                .orElseThrow(() -> new NotFoundException("Participant", userId));
        String cleanNote = TextSanitizer.plainText(note);
        if (cleanNote != null) {
            p.addHostEvidence(cleanNote);
        }
        if (markAttended) {
            AttendanceStatus from = p.getAttendanceStatus();
            AttendanceStatus to = from == AttendanceStatus.NO_SHOW ? AttendanceStatus.ATTENDED : AttendanceStatus.CHECKED_IN;
            if (to == AttendanceStatus.CHECKED_IN) {
                p.markCheckedIn(Instant.now(clock), false);
            } else {
                p.moveTo(to);
            }
            log.record(p.getId(), from, to, Actor.HOST, cleanNote);
        }
        return p;
    }

    /**
     * The host reports that check-in was not working (no network at the ground, the code would not load).
     * Nobody on this activity gets a no-show penalty. Allowed from the start until scores become final.
     */
    @Transactional
    public void markCheckinUnreliable(UUID activityId, UUID hostId) {
        Activity activity = activity(activityId);
        requireHost(activity, hostId);
        Instant now = Instant.now(clock);
        if (now.isBefore(activity.getStartsAt())) {
            throw new RuleViolationException("activity-not-started", "Check-in can only be reported broken once the activity has started");
        }
        if (now.isAfter(activity.endsAt().plus(scoresFinalAfterEnd))) {
            throw new RuleViolationException("scores-final", "Scores for this activity are already final");
        }
        activity.markAttendanceUnreliable();
    }

    private Activity activity(UUID id) {
        return activities.findById(id).orElseThrow(() -> new NotFoundException("Activity", id));
    }

    private Participant joinedParticipant(UUID activityId, UUID userId) {
        return participants.findByActivityIdAndUserId(activityId, userId)
                .filter(Participant::isJoined)
                .orElseThrow(() -> new NotFoundException("Participation of user", userId));
    }

    private static void requireHost(Activity activity, UUID userId) {
        if (!activity.isHostedBy(userId)) {
            throw new ForbiddenException("Only the host can do this");
        }
    }

    private void requireCheckinWindow(Activity activity, Instant now) {
        if (now.isBefore(activity.getStartsAt().minus(opensBefore))) {
            throw new RuleViolationException("checkin-not-open", "Check-in opens " + opensBefore.toMinutes() + " minutes before the start");
        }
        if (now.isAfter(activity.getStartsAt().plus(grace))) {
            throw new RuleViolationException("checkin-closed", "Check-in closed " + grace.toMinutes() + " minutes after the start");
        }
    }

    private static void requireOpen(Activity activity) {
        if (!activity.isOpen()) {
            throw new RuleViolationException("activity-not-open", "This activity was cancelled");
        }
    }
}
