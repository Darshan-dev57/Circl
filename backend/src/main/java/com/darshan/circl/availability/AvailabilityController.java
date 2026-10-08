package com.darshan.circl.availability;

import com.darshan.circl.activity.Category;
import com.darshan.circl.availability.dto.AvailabilityResponse;
import com.darshan.circl.availability.dto.NearbyPerson;
import com.darshan.circl.availability.dto.PostAvailabilityRequest;
import com.darshan.circl.common.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/availability")
@Validated
public class AvailabilityController {

    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @PostMapping
    public AvailabilityResponse post(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PostAvailabilityRequest body) {
        return service.post(CurrentUser.id(jwt), body);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> stop(@AuthenticationPrincipal Jwt jwt) {
        service.stop(CurrentUser.id(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/nearby")
    public List<NearbyPerson> nearby(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
                                     @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
                                     @RequestParam Category category,
                                     @RequestParam(defaultValue = "3") @DecimalMin("0.5") double radiusKm) {
        return service.nearby(CurrentUser.id(jwt), lat, lng, category, radiusKm);
    }
}
