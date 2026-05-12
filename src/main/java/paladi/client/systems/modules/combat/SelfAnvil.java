/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.combat;

import paladi.client.events.game.OpenScreenEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;

public class SelfAnvil extends Module {
    public SelfAnvil() {
        super(Categories.Combat, "self-anvil", "Automatically places an anvil on you to prevent other players from going into your hole.");
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof AnvilScreen) event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (BlockUtils.place(mc.player.getBlockPos().add(0, 2, 0), InvUtils.findInHotbar(itemStack -> Block.getBlockFromItem(itemStack.getItem()) instanceof AnvilBlock), 0)) {
            toggle();
        }
    }
}
