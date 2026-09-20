package com.darshan.circl.sim;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatSimulatorTest {

    static Stream<IntFunction<SeatCounter>> safeCounters() {
        return Stream.of(SynchronizedSeatCounter::new, AtomicSeatCounter::new, LockSeatCounter::new);
    }

    @ParameterizedTest
    @MethodSource("safeCounters")
    void safeCountersNeverOverbook(IntFunction<SeatCounter> factory) throws InterruptedException {
        for (int run = 0; run < 20; run++) {
            SeatSimulator.Result r = SeatSimulator.run(factory.apply(10), 100);
            assertEquals(10, r.winners(), r.counter() + " gave out the wrong number of seats");
            assertEquals(10, r.taken());
            assertEquals(0, r.overbooked());
        }
    }

    @Test
    void unsafeCounterOverbooks() throws InterruptedException {
        SeatSimulator.Result r = SeatSimulator.run(new UnsafeSeatCounter(10), 100);
        assertTrue(r.overbooked() > 0, "expected the unlocked counter to overbook, got " + r);
    }

    @RepeatedTest(5)
    void moreSeatsThanPeopleEveryoneGetsOne() throws InterruptedException {
        SeatSimulator.Result r = SeatSimulator.run(new AtomicSeatCounter(50), 20);
        assertEquals(20, r.winners());
    }
}
