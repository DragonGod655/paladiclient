/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.player;

import paladi.client.gui.GuiTheme;
import paladi.client.gui.widgets.WWidget;
import paladi.client.settings.DoubleSetting;
import paladi.client.settings.Setting;
import paladi.client.settings.SettingGroup;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.Utils;

public class Reach extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> blockReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("extra-block-reach")
        .description("The distance to add to your block reach.")
        .sliderMax(1)
        .build()
    );

    private final Setting<Double> entityReach = sgGeneral.add(new DoubleSetting.Builder()
        .name("extra-entity-reach")
        .description("The distance to add to your entity reach.")
        .sliderMax(1)
        .build()
    );

    public Reach() {
        super(Categories.Player, "reach", "Gives you super long arms.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return theme.label("Note: on vanilla servers you may give yourself up to 4 blocks of additional reach for specific actions - " +
            "interacting with block entities (chests, furnaces, etc.) or with vehicles. This does not work on paper servers.", Utils.getWindowWidth() / 3.0);
    }

    public double blockReach() {
        return isActive() ? blockReach.get() : 0;
    }

    public double entityReach() {
        return isActive() ? entityReach.get() : 0;
    }
}
