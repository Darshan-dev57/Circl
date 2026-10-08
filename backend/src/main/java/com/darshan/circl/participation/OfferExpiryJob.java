package com.darshan.circl.participation;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class OfferExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OfferExpiryJob.class);

    private final WaitlistRepository waitlist;
    private final WaitlistOffers offers;
    private final Clock clock;

    public OfferExpiryJob(WaitlistRepository waitlist, WaitlistOffers offers, Clock clock) {
        this.waitlist = waitlist;
        this.offers = offers;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${circl.waitlist.expiry-check:PT30S}")
    @SchedulerLock(name = "waitlist-offer-expiry", lockAtMostFor = "PT2M")
    public void run() {
        expireDueOffers();
    }

    /** each offer is released in its own transaction; returns how many were expired */
    public int expireDueOffers() {
        int expired = 0;
        for (WaitlistEntry offer : waitlist.findExpiredOffers(Instant.now(clock), PageRequest.of(0, 100))) {
            if (offers.expire(offer.getActivityId(), offer.getId())) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Expired {} waitlist offers", expired);
        }
        return expired;
    }
}
