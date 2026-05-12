/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.mixininterface.IPlayerInteractEntityC2SPacket;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.movement.NoSlow;
import paladi.client.systems.modules.movement.Sneak;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static paladi.client.PaladiClient.mc;

@Mixin(PlayerInteractEntityC2SPacket.class)
public abstract class PlayerInteractEntityC2SPacketMixin implements IPlayerInteractEntityC2SPacket {
    @Shadow @Final private PlayerInteractEntityC2SPacket.InteractTypeHandler type;
    @Shadow @Final private int entityId;

    @Override
    public PlayerInteractEntityC2SPacket.InteractType Paladi$getType() {
        return type.getType();
    }

    @Override
    public Entity Paladi$getEntity() {
        return mc.world.getEntityById(entityId);
    }

    @ModifyVariable(method = "<init>(IZLnet/minecraft/network/packet/c2s/play/PlayerInteractEntityC2SPacket$InteractTypeHandler;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static boolean setSneaking(boolean sneaking) {
        return Modules.get().get(Sneak.class).doPacket() || Modules.get().get(NoSlow.class).airStrict() || sneaking;
    }
}
