package com.darshan.circl.activity;

import com.darshan.circl.activity.dto.ActivityDetail;
import com.darshan.circl.activity.dto.ActivitySummary;
import com.darshan.circl.activity.dto.CreateActivityRequest;
import com.darshan.circl.activity.dto.UpdateActivityRequest;
import com.darshan.circl.common.error.ForbiddenException;
import com.darshan.circl.common.error.NotFoundException;
import com.darshan.circl.common.error.RuleViolationException;
import com.darshan.circl.common.outbox.Outbox;
import com.darshan.circl.common.text.TextSanitizer;
import com.darshan.circl.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ActivityService {

    private final ActivityRepository activities;
    private final ActivityMapper mapper;
    private final ApplicationEventPublisher events;
    private final Outbox outbox;
    private final Clock clock;

    public ActivityService(ActivityRepository activities, ActivityMapper mapper, ApplicationEventPublisher events,
                           Outbox outbox, Clock clock) {
        this.activities = activities;
        this.mapper = mapper;
        this.events = events;
        this.outbox = outbox;
        this.clock = clock;
    }

    @CacheEvict(cacheNames = CacheConfig.NEARBY, allEntries = true)
    @Transactional
    public ActivityDetail create(UUID hostId, CreateActivityRequest req) {
        Activity activity = new Activity(
                hostId,
                TextSanitizer.clean(req.title()),
                req.category(),
                TextSanitizer.plainText(req.description()),
                req.lat(),
                req.lng(),
                req.startsAt(),
                req.durationMinutes() == null ? 60 : req.durationMinutes(),
                req.capacity());
        if (req.minReliability() != null) {
            activity.setMinReliability(req.minReliability());
        }
        return mapper.toDetail(activities.save(activity));
    }

    @Transactional(readOnly = true)
    public ActivityDetail get(UUID id) {
        return mapper.toDetail(find(id));
    }

    @Transactional(readOnly = true)
    public Page<ActivitySummary> listUpcoming(Category category, Pageable pageable) {
        Instant now = Instant.now(clock);
        Page<Activity> page = category == null
                ? activities.findByStatusAndStartsAtAfter(ActivityStatus.OPEN, now, pageable)
                : activities.findByStatusAndCategoryAndStartsAtAfter(ActivityStatus.OPEN, category, now, pageable);
        return page.map(mapper::toSummary);
    }

    /**
     * Cached for 30 s per ~110 m cell (the point is rounded to 3 decimals before the query, so the
     * cached answer is exactly the answer for that cell). seatsLeft in here is for display only;
     * joining always goes to Postgres.
     */
    @Cacheable(cacheNames = CacheConfig.NEARBY, sync = true,
            key = "T(String).format('%.3f:%.3f:%s:%s:%d', #lat, #lng, #radiusKm, #category, #limit)")
    @Transactional(readOnly = true)
    public List<ActivitySummary> nearby(double lat, double lng, double radiusKm, Category category, int limit) {
        return activities.findNearby(lat, lng, radiusKm * 1000, category == null ? null : category.name(),
                        Instant.now(clock), limit)
                .stream()
                .map(r -> new ActivitySummary(r.getId(), r.getTitle(), Category.valueOf(r.getCategory()),
                        r.getStartsAt(), r.getCapacity(), r.getCapacity() - r.getSeatsTaken(),
                        Math.round(r.getDistanceM() * 10) / 10.0))
                .toList();
    }

    @CacheEvict(cacheNames = CacheConfig.NEARBY, allEntries = true)
    @Transactional
    public ActivityDetail update(UUID id, UUID userId, boolean admin, UpdateActivityRequest req) {
        Activity activity = activities.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Activity", id));
        requireHost(activity, userId, admin);
        if (!activity.isOpen()) {
            throw new RuleViolationException("activity-not-open", "A cancelled activity cannot be edited");
        }
        if (req.title() != null) {
            activity.setTitle(TextSanitizer.clean(req.title()));
        }
        if (req.description() != null) {
            activity.setDescription(TextSanitizer.plainText(req.description()));
        }
        if (req.startsAt() != null) {
            activity.setStartsAt(req.startsAt());
        }
        if (req.durationMinutes() != null) {
            activity.setDurationMinutes(req.durationMinutes());
        }
        if (req.minReliability() != null) {
            activity.setMinReliability(req.minReliability());
        }
        if (req.capacity() != null) {
            if (req.capacity() < activity.getSeatsTaken()) {
                throw new RuleViolationException("capacity-below-seats-taken",
                        "Capacity cannot go below the " + activity.getSeatsTaken() + " seats already taken");
            }
            boolean grew = req.capacity() > activity.getCapacity();
            activity.setCapacity(req.capacity());
            if (grew) {
                events.publishEvent(new ActivityCapacityIncreased(activity));
            }
        }
        return mapper.toDetail(activity);
    }

    @CacheEvict(cacheNames = CacheConfig.NEARBY, allEntries = true)
    @Transactional
    public void cancel(UUID id, UUID userId, boolean admin) {
        Activity activity = find(id);
        requireHost(activity, userId, admin);
        activity.cancel();
        outbox.append(id, "ActivityCancelled", Map.of("activityId", id));
    }

    /** ownership is checked per resource: being a HOST does not let you edit someone else's activity */
    private static void requireHost(Activity activity, UUID userId, boolean admin) {
        if (!admin && !activity.isHostedBy(userId)) {
            throw new ForbiddenException("Only the host can change this activity");
        }
    }

    private Activity find(UUID id) {
        return activities.findById(id).orElseThrow(() -> new NotFoundException("Activity", id));
    }
}
