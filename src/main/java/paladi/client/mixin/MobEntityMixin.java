/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.movement.EntityControl;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @ModifyReturnValue(method = "hasSaddleEquipped", at = @At("RETURN"))
    private boolean hasSaddleEquipped(boolean original) {
        return Modules.get().get(EntityControl.class).spoofSaddle() || original;
    }
}
