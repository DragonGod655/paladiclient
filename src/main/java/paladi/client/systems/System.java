/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems;

import paladi.client.PaladiClient;
import paladi.client.utils.files.StreamUtils;
import paladi.client.utils.misc.ISerializable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.crash.CrashException;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public abstract class System<T> implements ISerializable<T> {
    private final String name;
    private File file;

    protected boolean isFirstInit;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    public System(String name) {
        this.name = name;

        if (name != null) {
            this.file = new File(PaladiClient.FOLDER, name + ".nbt");
            this.isFirstInit = !file.exists();
        }
    }

    public void init() {}

    public void save(File folder) {
        File file = getFile();
        if (file == null) return;

        NbtCompound tag = toTag();
        if (tag == null) return;

        try {
            File tempFile = File.createTempFile(PaladiClient.MOD_ID, file.getName());
            NbtIo.write(tag, tempFile.toPath());

            if (folder != null) file = new File(folder, file.getName());

            file.getParentFile().mkdirs();

            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                StreamUtils.copy(tempFile, file);
            }

            tempFile.delete();
        } catch (IOException e) {
            PaladiClient.LOG.error("Error saving {}. Possibly corrupted?", this.name, e);
        }
    }

    public void save() {
        save(null);
    }

    public void load(File folder) {
        File file = getFile();
        if (file == null) return;

        try {
            if (folder != null) file = new File(folder, file.getName());

            if (file.exists()) {
                try {
                    fromTag(NbtIo.read(file.toPath()));
                } catch (CrashException e) {
                    String backupName = FilenameUtils.removeExtension(file.getName()) + "-" + ZonedDateTime.now().format(DATE_TIME_FORMATTER) + ".backup.nbt";
                    File backup = new File(file.getParentFile(), backupName);

                    try {
                        Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException ex) {
                        StreamUtils.copy(file, backup);
                    }

                    PaladiClient.LOG.error("Error loading {}. Possibly corrupted?", this.name, e);
                    PaladiClient.LOG.info("Saved settings backup to '{}'.", backup);
                }
            }
        } catch (IOException e) {
            PaladiClient.LOG.error("Error loading {}. Possibly corrupted?", this.name, e);
        }
    }

    public void load() {
        load(null);
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    @Override
    public NbtCompound toTag() {
        return null;
    }

    @Override
    public T fromTag(NbtCompound tag) {
        return null;
    }
}
