package com.pdipatria.sensibo.config;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class DeviceConfiguration {
    private final List<String> ids;
    public DeviceConfiguration(@Value("${sensibo.device-ids:}") String plural,
                               @Value("${sensibo.device-id:}") String legacy) {
        String setting = plural.isBlank() ? legacy : plural;
        var ordered = new LinkedHashSet<String>();
        for (String entry : setting.split(",", -1)) {
            Assert.hasText(entry, "Device IDs must not contain empty entries");
            ordered.add(entry.trim());
        }
        ids = List.copyOf(ordered);
    }
    public List<String> ids() { return ids; }
}
