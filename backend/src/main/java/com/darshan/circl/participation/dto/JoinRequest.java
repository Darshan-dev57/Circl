package com.darshan.circl.participation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** partySize = you plus friends you bring, 1 to 4 */
public record JoinRequest(@Min(1) @Max(4) Integer partySize) {

    public int partySizeOrDefault() {
        return partySize == null ? 1 : partySize;
    }
}
