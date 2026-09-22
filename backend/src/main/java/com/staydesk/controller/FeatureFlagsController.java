package com.staydesk.controller;

import com.staydesk.model.FeatureFlagsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeatureFlagsController {

    private final boolean multiRoomBookingEnabled;

    public FeatureFlagsController(@Value("${MULTI_ROOM_BOOKING_ENABLED:false}") boolean multiRoomBookingEnabled) {
        this.multiRoomBookingEnabled = multiRoomBookingEnabled;
    }

    @GetMapping("/feature-flags")
    public FeatureFlagsResponse getFeatureFlags() {
        return new FeatureFlagsResponse(multiRoomBookingEnabled);
    }
}
