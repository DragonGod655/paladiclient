/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.world;

import paladi.client.events.packets.PacketEvent;
import paladi.client.mixin.BlockHitResultAccessor;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.math.Direction;

public class BuildHeight extends Module {
    public BuildHeight() {
        super(Categories.World, "build-height", "Allows you to interact with objects at the build limit.");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerInteractBlockC2SPacket p)) return;
        if (mc.world == null) return;
        if (p.getBlockHitResult().getPos().y >= mc.world.getHeight() && p.getBlockHitResult().getSide() == Direction.UP) {
            ((BlockHitResultAccessor) p.getBlockHitResult()).Paladi$setSide(Direction.DOWN);
        }
    }
}
