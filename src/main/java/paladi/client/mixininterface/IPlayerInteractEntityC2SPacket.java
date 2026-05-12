/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixininterface;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

public interface IPlayerInteractEntityC2SPacket {
    PlayerInteractEntityC2SPacket.InteractType Paladi$getType();

    Entity Paladi$getEntity();
}
