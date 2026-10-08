package com.darshan.circl.reliability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReliabilityScoreTest {

    @Test
    void newUserStartsAtFifty() {
        assertThat(ReliabilityService.score(0, 0)).isEqualTo(50);
    }

    @Test
    void oneNoShowDoesNotSinkANewUser() {
        assertThat(ReliabilityService.score(0, 1)).isEqualTo(33);
        assertThat(ReliabilityService.score(1, 1)).isEqualTo(67);
    }

    @Test
    void longHistoryDominates() {
        assertThat(ReliabilityService.score(18, 20)).isEqualTo(86);
        assertThat(ReliabilityService.score(5, 20)).isEqualTo(27);
    }
}
