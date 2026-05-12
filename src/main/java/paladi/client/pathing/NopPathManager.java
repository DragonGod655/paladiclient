/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.pathing;

import paladi.client.settings.BoolSetting;
import paladi.client.settings.Setting;
import paladi.client.settings.Settings;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import java.util.function.Predicate;

public class NopPathManager implements IPathManager {
    private final NopSettings settings = new NopSettings();

    @Override
    public String getName() {
        return "none";
    }

    @Override
    public boolean isPathing() {
        return false;
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void stop() {}

    @Override
    public void moveTo(BlockPos pos, boolean ignoreY) {}

    @Override
    public void moveInDirection(float yaw) {}

    @Override
    public void mine(Block... blocks) {}

    @Override
    public void follow(Predicate<Entity> entity) {}

    @Override
    public float getTargetYaw() {
        return 0;
    }

    @Override
    public float getTargetPitch() {
        return 0;
    }

    @Override
    public ISettings getSettings() {
        return settings;
    }

    private static class NopSettings implements ISettings {
        private final Settings settings = new Settings();
        private final Setting<Boolean> setting = new BoolSetting.Builder().build();

        @Override
        public Settings get() {
            return settings;
        }

        @Override
        public Setting<Boolean> getWalkOnWater() {
            setting.reset();
            return setting;
        }

        @Override
        public Setting<Boolean> getWalkOnLava() {
            setting.reset();
            return setting;
        }

        @Override
        public Setting<Boolean> getStep() {
            setting.reset();
            return setting;
        }

        @Override
        public Setting<Boolean> getNoFall() {
            setting.reset();
            return setting;
        }

        @Override
        public void save() {}
    }
}
