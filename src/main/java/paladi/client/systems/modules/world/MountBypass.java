/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.world;

import paladi.client.events.packets.PacketEvent;
import paladi.client.mixininterface.IPlayerInteractEntityC2SPacket;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

public class MountBypass extends Module {
    private boolean dontCancel;

    public MountBypass() {
        super(Categories.World, "mount-bypass", "Allows you to bypass the IllegalStacks plugin and put chests on entities.");
    }

    @EventHandler
    public void onSendPacket(PacketEvent.Send event) {
        if (dontCancel) {
            dontCancel = false;
            return;
        }

        if (event.packet instanceof IPlayerInteractEntityC2SPacket packet) {
            if (packet.Paladi$getType() == PlayerInteractEntityC2SPacket.InteractType.INTERACT_AT && packet.Paladi$getEntity() instanceof AbstractDonkeyEntity) event.cancel();
        }
    }
}
