package com.darshan.circl.participation;

import com.darshan.circl.activity.Activity;
import com.darshan.circl.activity.ActivityRepository;
import com.darshan.circl.common.error.ForbiddenException;
import com.darshan.circl.common.error.NotFoundException;
import com.darshan.circl.common.web.Cursor;
import com.darshan.circl.participation.dto.MyActivitiesPage;
import com.darshan.circl.participation.dto.MyActivity;
import com.darshan.circl.participation.dto.ParticipantView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MyActivitiesService {

    private static final UUID MIN_ID = new UUID(0, 0);
    private static final UUID MAX_ID = new UUID(-1, -1);

    private final ParticipantRepository participants;
    private final ActivityRepository activities;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MyActivitiesService(ParticipantRepository participants, ActivityRepository activities, JdbcTemplate jdbc,
                               Clock clock) {
        this.participants = participants;
        this.activities = activities;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyActivitiesPage mine(UUID userId, boolean past, String cursor, int size) {
        Instant now = Instant.now(clock);
        Cursor after = cursor == null ? null : Cursor.decode(cursor);
        // fetch one extra row to know whether there is a next page
        List<MyActivityRow> rows = past
                ? participants.findPast(userId, now, after == null ? Instant.parse("9999-12-31T00:00:00Z") : after.startsAt(),
                        after == null ? MAX_ID : after.id(), size + 1)
                : participants.findUpcoming(userId, now, after == null ? Instant.EPOCH : after.startsAt(),
                        after == null ? MIN_ID : after.id(), size + 1);
        boolean more = rows.size() > size;
        List<MyActivity> items = rows.stream().limit(size)
                .map(r -> new MyActivity(r.getActivityId(), r.getTitle(), r.getCategory(), r.getStartsAt(),
                        r.getActivityStatus(), r.getParticipantId(), r.getPartySize(), r.getAttendanceStatus()))
                .toList();
        String next = more ? new Cursor(items.get(items.size() - 1).startsAt(), items.get(items.size() - 1).activityId()).encode() : null;
        return new MyActivitiesPage(items, next);
    }

    @Transactional(readOnly = true)
    public List<ParticipantView> participantsOf(UUID activityId, UUID hostId) {
        Activity activity = activities.findById(activityId).orElseThrow(() -> new NotFoundException("Activity", activityId));
        if (!activity.isHostedBy(hostId)) {
            throw new ForbiddenException("Only the host can see the participant list");
        }
        return jdbc.query("""
                SELECT p.id, p.user_id, u.name, p.party_size, p.attendance_status, p.late, p.joined_at
                  FROM participants p JOIN users u ON u.id = p.user_id
                 WHERE p.activity_id = ? AND p.status = 'JOINED'
                 ORDER BY p.joined_at
                """, (rs, i) -> new ParticipantView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getInt(4), rs.getString(5), rs.getBoolean(6), rs.getTimestamp(7).toInstant()), activityId);
    }
}
