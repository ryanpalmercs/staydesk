package com.staydesk.service;

import com.staydesk.model.dto.ActivityVersionDto;
import com.staydesk.repository.ActivityRepository;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;

    public ActivityService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public ActivityVersionDto getVersion() {
        return new ActivityVersionDto(activityRepository.getLatestActivityTimestamp());
    }
}
