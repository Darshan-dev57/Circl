package com.darshan.circl.activity;

import com.darshan.circl.activity.dto.ActivityDetail;
import com.darshan.circl.activity.dto.ActivitySummary;
import org.springframework.stereotype.Component;

@Component
public class ActivityMapper {

    public ActivitySummary toSummary(Activity a) {
        return new ActivitySummary(a.getId(), a.getTitle(), a.getCategory(), a.getStartsAt(),
                a.getCapacity(), a.seatsLeft(), null);
    }

    public ActivityDetail toDetail(Activity a) {
        return new ActivityDetail(a.getId(), a.getHostId(), a.getTitle(), a.getCategory(), a.getDescription(),
                a.getLatitude(), a.getLongitude(), a.getStartsAt(), a.endsAt(),
                a.getCapacity(), a.getSeatsTaken(), a.seatsLeft(), a.getStatus(), a.getCreatedAt());
    }
}
