package com.staydesk.controller;

import com.staydesk.model.dto.ActivityVersionDto;
import com.staydesk.service.ActivityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activity")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("version")
    public ActivityVersionDto getVersion() {
        return activityService.getVersion();
    }
}
