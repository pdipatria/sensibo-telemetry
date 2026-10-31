package com.pdipatria.sensibo.web;

import com.pdipatria.sensibo.service.ScheduledPolling;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("poll")
@RequestMapping("/api/polling")
public class PollingController {
    private final ScheduledPolling polling;

    public PollingController(ScheduledPolling polling) {
        this.polling = polling;
    }

    @PostMapping({"/start", "/start/{deviceId}"})
    public ScheduledPolling.Envelope start(@PathVariable(required = false) String deviceId) {
        return polling.start(deviceId == null ? "*" : deviceId);
    }

    @PostMapping({"/stop", "/stop/{deviceId}"})
    public ScheduledPolling.Envelope stop(@PathVariable(required = false) String deviceId) {
        return polling.stop(deviceId == null ? "*" : deviceId);
    }

    @GetMapping({"/status", "/status/{deviceId}"})
    public ScheduledPolling.Envelope status(@PathVariable(required = false) String deviceId) {
        return polling.status(deviceId == null ? "*" : deviceId);
    }
}
