/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.player;

import paladi.client.events.world.TickEvent;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;

public class EXPThrower extends Module {
    public EXPThrower() {
        super(Categories.Player, "exp-thrower", "Automatically throws XP bottles from your hotbar.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        FindItemResult exp = InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE);
        if (!exp.found()) return;

        Rotations.rotate(mc.player.getYaw(), 90, () -> {
            if (exp.getHand() != null) {
                mc.interactionManager.interactItem(mc.player, exp.getHand());
            }
            else {
                InvUtils.swap(exp.slot(), true);
                mc.interactionManager.interactItem(mc.player, exp.getHand());
                InvUtils.swapBack();
            }
        });
    }
}
