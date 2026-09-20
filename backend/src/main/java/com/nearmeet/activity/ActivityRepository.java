package com.nearmeet.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Page<Activity> findByStatusAndStartsAtAfter(ActivityStatus status, Instant after, Pageable pageable);

    Page<Activity> findByStatusAndCategoryAndStartsAtAfter(ActivityStatus status, Category category,
                                                           Instant after, Pageable pageable);
}
