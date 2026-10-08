package com.nearmeet.sim;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * 100 people tap "Join" on an activity with 10 seats at the same moment.
 * All threads wait on a start latch so they really hit the counter together.
 */
public class SeatSimulator {

    public record Result(String counter, int capacity, int threads, int winners, int taken) {
        public int overbooked() {
            return Math.max(0, winners - capacity);
        }
    }

    public static Result run(SeatCounter counter, int threads) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (counter.tryTake()) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        return new Result(counter.name(), counter.capacity(), threads, winners.get(), counter.taken());
    }

    public static void main(String[] args) throws InterruptedException {
        int seats = 10;
        int threads = 100;
        List<IntFunction<SeatCounter>> counters = List.of(UnsafeSeatCounter::new);

        System.out.printf("%d threads, %d seats%n", threads, seats);
        for (IntFunction<SeatCounter> factory : counters) {
            Result r = run(factory.apply(seats), threads);
            System.out.printf("%-28s winners=%3d taken=%3d overbooked=%d%n",
                    r.counter(), r.winners(), r.taken(), r.overbooked());
        }
    }
}
