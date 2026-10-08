package com.darshan.circl.participation;

import com.darshan.circl.common.web.CurrentUser;
import com.darshan.circl.participation.dto.OfferResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WaitlistController {

    private final WaitlistOffers offers;
    private final WaitlistRepository waitlist;

    public WaitlistController(WaitlistOffers offers, WaitlistRepository waitlist) {
        this.offers = offers;
        this.waitlist = waitlist;
    }

    @GetMapping("/me/offers")
    public List<OfferResponse> myOffers(@AuthenticationPrincipal Jwt jwt) {
        return waitlist.findByUserIdAndStatusIn(CurrentUser.id(jwt), List.of(WaitlistStatus.OFFERED))
                .stream().map(OfferResponse::of).toList();
    }

    @PostMapping("/waitlist/offers/{offerId}/claim")
    public Map<String, Object> claim(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID offerId) {
        Participant p = offers.claim(offerId, CurrentUser.id(jwt));
        return Map.of("activityId", p.getActivityId(), "status", "JOINED", "partySize", p.getPartySize());
    }

    @PostMapping("/waitlist/offers/{offerId}/decline")
    public ResponseEntity<Void> decline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID offerId) {
        offers.decline(offerId, CurrentUser.id(jwt));
        return ResponseEntity.noContent().build();
    }
}
