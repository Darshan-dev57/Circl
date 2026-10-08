package com.darshan.circl.participation;

import com.darshan.circl.TestcontainersConfig;
import com.darshan.circl.identity.Role;
import com.darshan.circl.identity.TokenService;
import com.darshan.circl.identity.User;
import com.darshan.circl.identity.UserRepository;
import com.darshan.circl.participation.dto.JoinResponse.JoinOutcome;
import com.darshan.circl.participation.engine.JoinStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many users hit Join on the same activity at the same instant.
 * Whatever the strategy, nobody may get a seat that does not exist.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class JoinRaceIT {

    private static final int RUNS = Integer.getInteger("circl.race.runs", 5);

    @LocalServerPort
    int port;

    @Autowired
    ParticipationService participation;

    @Autowired
    UserRepository users;

    @Autowired
    TokenService tokens;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sixtyParallelHttpJoinsForTenSeats() throws Exception {
        UUID activity = newActivity(10, 0);
        List<User> people = newUsers(60);
        HttpClient client = HttpClient.newHttpClient();

        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger joined = new AtomicInteger();
        AtomicInteger waitlisted = new AtomicInteger();
        runTogether(people.size(), i -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/activities/" + activity + "/join"))
                    .header("Authorization", "Bearer " + tokens.accessToken(people.get(i)))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            statuses.add(res.statusCode());
            if (res.body().contains("\"JOINED\"")) {
                joined.incrementAndGet();
            } else if (res.body().contains("\"WAITLISTED\"")) {
                waitlisted.incrementAndGet();
            }
            return null;
        });

        assertThat(statuses).hasSize(60).allMatch(s -> s == 200);
        assertThat(joined.get()).isEqualTo(10);
        assertThat(waitlisted.get()).isEqualTo(50);
        assertThat(seatsTaken(activity)).isEqualTo(10);
        assertThat(count("SELECT count(*) FROM participants WHERE activity_id = ? AND status = 'JOINED'", activity)).isEqualTo(10);
        assertThat(count("SELECT count(*) FROM waitlist WHERE activity_id = ?", activity)).isEqualTo(50);
        record("HTTP, conditional update, 60 users / 10 seats: joined=%d waitlisted=%d seats_taken=%d"
                .formatted(joined.get(), waitlisted.get(), seatsTaken(activity)));
    }

    @ParameterizedTest
    @EnumSource(JoinStrategy.class)
    void fiftyThreadsForTheLastSeat(JoinStrategy strategy) throws Exception {
        List<User> people = newUsers(50);
        List<Long> latenciesMicros = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger extraAttempts = new AtomicInteger();
        int overbooked = 0;

        for (int run = 0; run < RUNS; run++) {
            UUID activity = newActivity(2, 1); // one seat left
            AtomicInteger joined = new AtomicInteger();
            AtomicInteger waitlisted = new AtomicInteger();

            runTogether(people.size(), i -> {
                long start = System.nanoTime();
                var result = participation.join(activity, people.get(i).getId(), 1, UUID.randomUUID().toString(), strategy);
                latenciesMicros.add((System.nanoTime() - start) / 1_000);
                extraAttempts.addAndGet(result.attempts() - 1);
                if (result.response().status() == JoinOutcome.JOINED) {
                    joined.incrementAndGet();
                } else {
                    waitlisted.incrementAndGet();
                }
                return null;
            });

            int taken = seatsTaken(activity);
            overbooked += Math.max(0, taken - 2);
            assertThat(joined.get()).as("run %d with %s", run, strategy).isEqualTo(1);
            assertThat(waitlisted.get()).isEqualTo(49);
            assertThat(taken).isEqualTo(2);
        }

        List<Long> sorted = new ArrayList<>(latenciesMicros);
        Collections.sort(sorted);
        long p50 = sorted.get(sorted.size() / 2);
        long p95 = sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
        record("%s: runs=%d joins=%d overbooked=%d retries=%d p50=%.1fms p95=%.1fms"
                .formatted(strategy, RUNS, sorted.size(), overbooked, extraAttempts.get(), p50 / 1000.0, p95 / 1000.0));
        assertThat(overbooked).isZero();
    }

    @Test
    void tenParallelRetriesWithTheSameKeyJoinOnce() throws Exception {
        UUID activity = newActivity(5, 0);
        User user = newUsers(1).get(0);
        AtomicInteger replays = new AtomicInteger();
        List<String> bodies = Collections.synchronizedList(new ArrayList<>());

        runTogether(10, i -> {
            try {
                var r = participation.join(activity, user.getId(), 1, "double-tap");
                if (r.replayed()) {
                    replays.incrementAndGet();
                }
                bodies.add(r.response().toString());
            } catch (com.darshan.circl.common.error.ConflictException e) {
                bodies.add("in-progress");
            }
            return null;
        });

        assertThat(count("SELECT count(*) FROM participants WHERE activity_id = ?", activity)).isEqualTo(1);
        assertThat(seatsTaken(activity)).isEqualTo(1);
        assertThat(bodies.stream().filter(b -> !b.equals("in-progress")).distinct()).hasSize(1);
    }

    private interface Task {
        Void run(int index) throws Exception;
    }

    private static void runTogether(int threads, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int index = i;
            Callable<Void> c = () -> {
                start.await();
                return task.run(index);
            };
            futures.add(pool.submit(c));
        }
        start.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();
    }

    private UUID newActivity(int capacity, int alreadyTaken) {
        User host = users.save(new User("host-" + UUID.randomUUID() + "@race.dev", "Host", "x", Role.HOST));
        return jdbc.queryForObject("""
                INSERT INTO activities (host_id, title, category, latitude, longitude, starts_at, capacity, seats_taken)
                VALUES (?, 'Race', 'FOOTBALL', 12.97, 77.59, ?, ?, ?) RETURNING id
                """, UUID.class, host.getId(), java.sql.Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)),
                capacity, alreadyTaken);
    }

    private List<User> newUsers(int n) {
        List<User> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(users.save(new User("u-" + UUID.randomUUID() + "@race.dev", "User " + i, "x", Role.PARTICIPANT)));
        }
        return list;
    }

    private int seatsTaken(UUID activity) {
        return jdbc.queryForObject("SELECT seats_taken FROM activities WHERE id = ?", Integer.class, activity);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static void record(String line) throws IOException {
        System.out.println("RACE " + line);
        Files.writeString(Path.of("target", "race-results.txt"), line + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
