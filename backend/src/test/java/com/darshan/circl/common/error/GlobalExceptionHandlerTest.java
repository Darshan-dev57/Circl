package com.darshan.circl.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void redisDownIs503NotA500() {
        assertThat(handler.handleStoreDown(new RedisConnectionFailureException("refused")).getStatus())
                .isEqualTo(503);
        // what an already open connection reports when the Redis container is stopped
        assertThat(handler.handleStoreDown(new QueryTimeoutException("Redis command timed out")).getStatus())
                .isEqualTo(503);
    }

    @Test
    void unexpectedErrorHidesTheMessage() {
        var pd = handler.handleUnexpected(new IllegalStateException("db password is hunter2"));
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).doesNotContain("hunter2");
    }
}
