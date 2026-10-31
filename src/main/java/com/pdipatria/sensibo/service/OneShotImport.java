package com.pdipatria.sensibo.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("import")
public class OneShotImport implements ApplicationRunner {
    private final HistoricalImportService importer;
    private final com.pdipatria.sensibo.config.DeviceConfiguration devices;

    public OneShotImport(HistoricalImportService importer, com.pdipatria.sensibo.config.DeviceConfiguration devices) {
        this.importer = importer;
        this.devices = devices;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean failed = false;
        for (String device : devices.ids()) {
            try { importer.importHistory(device); }
            catch (RuntimeException ex) { failed = true; }
        }
        if (failed) throw new IllegalStateException("One or more device imports failed");
    }
}
