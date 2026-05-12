/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.movement;

import paladi.client.settings.BoolSetting;
import paladi.client.settings.DoubleSetting;
import paladi.client.settings.Setting;
import paladi.client.settings.SettingGroup;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;

public class TridentBoost extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> multiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("boost")
        .description("How much your velocity is multiplied by when using riptide.")
        .defaultValue(2)
        .min(0.1)
        .sliderMin(1)
        .build()
    );

    private final Setting<Boolean> allowOutOfWater = sgGeneral.add(new BoolSetting.Builder()
        .name("out-of-water")
        .description("Whether riptide should work out of water")
        .defaultValue(true)
        .build()
    );

    public TridentBoost() {
        super(Categories.Movement, "trident-boost", "Boosts you when using riptide with a trident.");
    }

    public double getMultiplier() {
        return isActive() ? multiplier.get() : 1;
    }

    public boolean allowOutOfWater() {
        return isActive() ? allowOutOfWater.get() : false;
    }
}
