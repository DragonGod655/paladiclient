/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.player;

import paladi.client.settings.BoolSetting;
import paladi.client.settings.EntityTypeListSetting;
import paladi.client.settings.Setting;
import paladi.client.settings.SettingGroup;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.tag.ItemTags;

import java.util.Set;

public class NoMiningTrace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("blacklisted-entities")
        .description("Entities you will interact with as normal.")
        .defaultValue()
        .build()
    );

    private final Setting<Boolean> onlyWhenHoldingPickaxe = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-holding-a-pickaxe")
        .description("Whether or not to work only when holding a pickaxe.")
        .defaultValue(true)
        .build()
    );

    public NoMiningTrace() {
        super(Categories.Player, "no-mining-trace", "Allows you to mine blocks through entities.");
    }

    public boolean canWork(Entity entity) {
        if (!isActive()) return false;

        return (!onlyWhenHoldingPickaxe.get() || mc.player.getMainHandStack().isIn(ItemTags.PICKAXES) || mc.player.getOffHandStack().isIn(ItemTags.PICKAXES)) &&
            (entity == null || !entities.get().contains(entity.getType()));
    }
}
