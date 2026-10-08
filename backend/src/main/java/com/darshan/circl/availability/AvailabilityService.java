package com.darshan.circl.availability;

import com.darshan.circl.activity.Category;
import com.darshan.circl.availability.dto.AvailabilityResponse;
import com.darshan.circl.availability.dto.NearbyPerson;
import com.darshan.circl.availability.dto.PostAvailabilityRequest;
import com.darshan.circl.common.error.ForbiddenException;
import com.darshan.circl.common.error.NotFoundException;
import com.darshan.circl.common.error.RuleViolationException;
import com.darshan.circl.common.error.TooManyRequestsException;
import com.darshan.circl.identity.User;
import com.darshan.circl.identity.UserRepository;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * "Free now": a short-lived post that says "free for an hour, up for cricket".
 * Lives only in Redis with a TTL. Location is snapped to a ~1 km grid before it is stored,
 * and other people only ever see a distance bucket, never coordinates or metres.
 */
@Service
public class AvailabilityService {

    static final String EXPIRY_KEY = "avail:exp";

    private final StringRedisTemplate redis;
    private final UserRepository users;
    private final AvailabilityProperties props;
    private final Clock clock;

    public AvailabilityService(StringRedisTemplate redis, UserRepository users, AvailabilityProperties props, Clock clock) {
        this.redis = redis;
        this.users = users;
        this.props = props;
        this.clock = clock;
    }

    static String postKey(UUID userId) {
        return "avail:" + userId;
    }

    static String geoKey(Category category) {
        return "avail:geo:" + category.name();
    }

    /** two decimals of a degree is about 1.1 km, so the exact spot is never stored */
    static double snap(double degrees) {
        return Math.round(degrees * 100) / 100.0;
    }

    public AvailabilityResponse post(UUID userId, PostAvailabilityRequest req) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        removePost(userId);

        Instant until = Instant.now(clock).plus(Duration.ofMinutes(req.minutes()));
        String key = postKey(userId);
        redis.opsForHash().putAll(key, Map.of(
                "cat", req.category().name(),
                "until", String.valueOf(until.getEpochSecond()),
                "name", user.getName()));
        redis.expire(key, Duration.ofMinutes(req.minutes()));
        redis.opsForGeo().add(geoKey(req.category()), new Point(snap(req.lng()), snap(req.lat())), userId.toString());
        redis.opsForZSet().add(EXPIRY_KEY, userId.toString(), until.getEpochSecond());
        return new AvailabilityResponse(req.category(), until);
    }

    public void stop(UUID userId) {
        removePost(userId);
    }

    public List<NearbyPerson> nearby(UUID userId, double lat, double lng, Category category, double radiusKm) {
        if (radiusKm > props.maxRadiusKm()) {
            throw new RuleViolationException("radius-too-large", "Radius can be at most " + props.maxRadiusKm() + " km");
        }
        User me = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        if (me.getReliabilityScore() < props.minReliabilityToSearch()) {
            throw new ForbiddenException("A reliability score of " + props.minReliabilityToSearch() + " is needed to see who is free");
        }
        enforceRateLimit(userId);
        double myLat = snap(lat);
        double myLng = snap(lng);
        rejectTeleport(userId, myLat, myLng);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(
                geoKey(category),
                GeoReference.fromCoordinate(myLng, myLat),
                new Distance(radiusKm, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending().limit(50));

        List<NearbyPerson> people = new ArrayList<>();
        if (results == null) {
            return people;
        }
        long now = Instant.now(clock).getEpochSecond();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results) {
            String member = r.getContent().getName();
            if (member.equals(userId.toString())) {
                continue;
            }
            Map<Object, Object> post = redis.opsForHash().entries(postKey(UUID.fromString(member)));
            long until = post.isEmpty() ? 0 : Long.parseLong((String) post.get("until"));
            if (until <= now) {
                // the hash expired but GEO members never do, so clean it up here too
                redis.opsForGeo().remove(geoKey(category), member);
                redis.opsForZSet().remove(EXPIRY_KEY, member);
                continue;
            }
            people.add(new NearbyPerson(UUID.fromString(member), (String) post.get("name"), category,
                    Instant.ofEpochSecond(until), bucket(r.getDistance().getValue())));
        }
        return people;
    }

    static String bucket(double km) {
        if (km < 1.0) {
            return "<1 km";
        }
        return "~" + Math.round(km) + " km";
    }

    /** removes expired posts from the GEO sets; returns how many */
    public int cleanupExpired() {
        long now = Instant.now(clock).getEpochSecond();
        var expired = redis.opsForZSet().rangeByScore(EXPIRY_KEY, 0, now);
        if (expired == null || expired.isEmpty()) {
            return 0;
        }
        for (String member : expired) {
            for (Category c : Category.values()) {
                redis.opsForGeo().remove(geoKey(c), member);
            }
            redis.opsForZSet().remove(EXPIRY_KEY, member);
        }
        return expired.size();
    }

    private void removePost(UUID userId) {
        Object oldCategory = redis.opsForHash().get(postKey(userId), "cat");
        if (oldCategory != null) {
            redis.opsForGeo().remove(geoKey(Category.valueOf((String) oldCategory)), userId.toString());
        }
        redis.delete(postKey(userId));
        redis.opsForZSet().remove(EXPIRY_KEY, userId.toString());
    }

    private void enforceRateLimit(UUID userId) {
        String key = "avail:rl:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (Objects.equals(count, 1L)) {
            redis.expire(key, props.searchWindow());
        }
        if (count != null && count > props.searchesPerWindow()) {
            Long ttl = redis.getExpire(key);
            throw new TooManyRequestsException("Too many searches, try again in a few minutes",
                    ttl == null || ttl < 0 ? props.searchWindow().toSeconds() : ttl);
        }
    }

    /** Searching from points far apart in a short time is how someone would triangulate a person. */
    private void rejectTeleport(UUID userId, double lat, double lng) {
        String key = "avail:last:" + userId;
        String last = redis.opsForValue().get(key);
        if (last != null) {
            String[] parts = last.split(",");
            double km = haversineKm(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), lat, lng);
            if (km > props.maxJumpKm()) {
                throw new RuleViolationException("location-jump", "Your location changed too much since your last search");
            }
        }
        redis.opsForValue().set(key, lat + "," + lng, props.searchWindow());
    }

    static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
