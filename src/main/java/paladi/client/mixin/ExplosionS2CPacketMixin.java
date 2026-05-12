/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.mixininterface.IExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(ExplosionS2CPacket.class)
public abstract class ExplosionS2CPacketMixin implements IExplosionS2CPacket {
    @Shadow
    @Final
    @Mutable
    private Optional<Vec3d> playerKnockback;

    @Override
    public void Paladi$setVelocityX(float velocity) {
        if (playerKnockback.isPresent()) {
            Vec3d kb = playerKnockback.get();
            playerKnockback = Optional.of(new Vec3d(velocity, kb.y, kb.z));
        } else {
            playerKnockback = Optional.of(new Vec3d(velocity, 0, 0));
        }
    }

    @Override
    public void Paladi$setVelocityY(float velocity) {
        if (playerKnockback.isPresent()) {
            Vec3d kb = playerKnockback.get();
            playerKnockback = Optional.of(new Vec3d(kb.x, velocity, kb.z));
        } else {
            playerKnockback = Optional.of(new Vec3d(0, velocity, 0));
        }
    }

    @Override
    public void Paladi$setVelocityZ(float velocity) {
        if (playerKnockback.isPresent()) {
            Vec3d kb = playerKnockback.get();
            playerKnockback = Optional.of(new Vec3d(kb.x, kb.y, velocity));
        } else {
            playerKnockback = Optional.of(new Vec3d(0, 0, velocity));
        }
    }
}
