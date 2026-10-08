package com.darshan.circl.participation;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.common.error.ApiException;
import com.darshan.circl.identity.Role;
import com.darshan.circl.identity.User;
import com.darshan.circl.identity.UserRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Leaves, new joins, claims, declines and the expiry job all running at once.
 * Afterwards the seat count must still add up.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class WaitlistRaceIT {

    @Autowired
    ParticipationService participation;

    @Autowired
    WaitlistOffers offers;

    @Autowired
    OfferExpiryJob expiryJob;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @RepeatedTest(3)
    void cancelsClaimsAndJoinsKeepTheCountsConsistent() throws Exception {
        int capacity = 10;
        User host = newUser(Role.HOST);
        UUID activity = jdbc.queryForObject("""
                INSERT INTO activities (host_id, title, category, latitude, longitude, starts_at, capacity)
                VALUES (?, 'Mixed race', 'CRICKET', 12.97, 77.59, ?, ?) RETURNING id
                """, UUID.class, host.getId(), Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)), capacity);

        List<User> joined = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            User u = newUser(Role.PARTICIPANT);
            participation.join(activity, u.getId(), 1, UUID.randomUUID().toString());
            joined.add(u);
        }
        List<User> waiting = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            User u = newUser(Role.PARTICIPANT);
            participation.join(activity, u.getId(), 1 + (i % 3), UUID.randomUUID().toString());
            waiting.add(u);
        }
        List<User> latecomers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            latecomers.add(newUser(Role.PARTICIPANT));
        }

        List<Runnable> work = new ArrayList<>();
        joined.forEach(u -> work.add(() -> participation.leave(activity, u.getId())));
        latecomers.forEach(u -> work.add(() ->
                participation.join(activity, u.getId(), 1 + ThreadLocalRandom.current().nextInt(2), UUID.randomUUID().toString())));
        waiting.forEach(u -> work.add(() -> {
            for (int attempt = 0; attempt < 20; attempt++) {
                UUID offerId = openOffer(activity, u.getId());
                if (offerId != null) {
                    if (ThreadLocalRandom.current().nextInt(4) == 0) {
                        offers.decline(offerId, u.getId());
                    } else {
                        offers.claim(offerId, u.getId());
                    }
                    return;
                }
                sleep(10);
            }
        }));
        for (int i = 0; i < 5; i++) {
            work.add(() -> {
                jdbc.update("UPDATE waitlist SET claim_deadline = now() - interval '1 second' WHERE activity_id = ? AND status = 'OFFERED' AND random() < 0.3", activity);
                expiryJob.expireDueOffers();
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Runnable r : work) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    r.run();
                } catch (ApiException expected) {
                    // offer expired under us, already declined, etc.
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        int seatsTaken = jdbc.queryForObject("SELECT seats_taken FROM activities WHERE id = ?", Integer.class, activity);
        int ledger = jdbc.queryForObject("SELECT COALESCE(SUM(delta), 0) FROM capacity_ledger WHERE activity_id = ?", Integer.class, activity);
        int joinedSeats = jdbc.queryForObject("SELECT COALESCE(SUM(party_size), 0) FROM participants WHERE activity_id = ? AND status = 'JOINED'", Integer.class, activity);
        int heldSeats = jdbc.queryForObject("SELECT COALESCE(SUM(party_size), 0) FROM waitlist WHERE activity_id = ? AND status = 'OFFERED'", Integer.class, activity);

        assertThat(seatsTaken).isLessThanOrEqualTo(capacity);
        assertThat(ledger).isEqualTo(seatsTaken);
        assertThat(joinedSeats + heldSeats).isEqualTo(seatsTaken);
    }

    private UUID openOffer(UUID activity, UUID user) {
        List<UUID> ids = jdbc.queryForList("SELECT id FROM waitlist WHERE activity_id = ? AND user_id = ? AND status = 'OFFERED'",
                UUID.class, activity, user);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private User newUser(Role role) {
        return users.save(new User(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@race.dev", "U", "x", role));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
