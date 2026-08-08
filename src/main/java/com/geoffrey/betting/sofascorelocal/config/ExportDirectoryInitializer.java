package com.geoffrey.betting.sofascorelocal.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ExportDirectoryInitializer implements ApplicationRunner {

    private final SofascoreProperties properties;

    public ExportDirectoryInitializer(SofascoreProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path exportDirectory = properties.getExportDirectory().toAbsolutePath().normalize();
        Files.createDirectories(exportDirectory);
        if (!Files.isDirectory(exportDirectory) || !Files.isWritable(exportDirectory)) {
            throw new IllegalStateException("Export directory is not writable: " + exportDirectory);
        }
    }
}
