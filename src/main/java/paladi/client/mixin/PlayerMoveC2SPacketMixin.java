/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.mixininterface.IPlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerMoveC2SPacket.class)
public abstract class PlayerMoveC2SPacketMixin implements IPlayerMoveC2SPacket {
    @Unique private int tag;

    @Override
    public void Paladi$setTag(int tag) { this.tag = tag; }

    @Override
    public int Paladi$getTag() { return this.tag; }
}
