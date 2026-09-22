package com.darshan.circl.activity;

import com.darshan.circl.activity.dto.UpdateActivityRequest;
import com.darshan.circl.common.error.ForbiddenException;
import com.darshan.circl.common.error.NotFoundException;
import com.darshan.circl.common.error.RuleViolationException;
import com.darshan.circl.common.outbox.Outbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActivityServiceTest {

    ActivityRepository repo = mock(ActivityRepository.class);
    ActivityService service;

    @BeforeEach
    void setUp() {
        service = new ActivityService(repo, new ActivityMapper(), event -> { }, mock(Outbox.class), Clock.systemUTC());
    }

    @Test
    void capacityCannotDropBelowSeatsTaken() {
        Activity a = new Activity(UUID.randomUUID(), "Football", Category.FOOTBALL, null, 12.9, 77.6,
                Instant.now().plus(1, ChronoUnit.DAYS), 60, 10);
        a.setSeatsTaken(6);
        UUID id = UUID.randomUUID();
        when(repo.findByIdForUpdate(id)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.update(id, null, true, new UpdateActivityRequest(null, null, null, null, 5, null)))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("6 seats");
    }

    @Test
    void onlyTheHostCanEdit() {
        UUID hostId = UUID.randomUUID();
        Activity a = new Activity(hostId, "Coffee", Category.COFFEE, null, 12.9, 77.6,
                Instant.now().plus(1, ChronoUnit.DAYS), 60, 4);
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.cancel(id, UUID.randomUUID(), false))
                .isInstanceOf(ForbiddenException.class);
        service.cancel(id, hostId, false); // the host can
    }

    @Test
    void missingActivityIsNotFound() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
