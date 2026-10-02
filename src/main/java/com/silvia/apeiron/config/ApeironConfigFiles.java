package com.silvia.apeiron.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Owns Apeiron's configuration directory and preserves settings from the former single-file layout. */
public final class ApeironConfigFiles {

    public static final String DIRECTORY_NAME = "apeiron";

    private ApeironConfigFiles() {}

    public static File prepareDirectory(final File forgeConfigDirectory) {
        final File directory = new File(forgeConfigDirectory, DIRECTORY_NAME);
        final File legacy = new File(forgeConfigDirectory, ApeironConfig.FILE_NAME);
        final File current = new File(directory, ApeironConfig.FILE_NAME);
        try {
            Files.createDirectories(directory.toPath());
            if (!current.exists() && legacy.isFile()) {
                Files.move(legacy.toPath(), current.toPath());
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Cannot prepare Apeiron configuration directory " + directory, exception);
        }
        return directory;
    }
}
