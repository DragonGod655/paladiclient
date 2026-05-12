/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.player.PotionSaver;
import paladi.client.utils.Utils;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatusEffectInstance.class)
public abstract class StatusEffectInstanceMixin {
    @Inject(method = "updateDuration", at = @At("HEAD"), cancellable = true)
    private void tick(CallbackInfo info) {
        if (!Utils.canUpdate()) return;

        if (Modules.get().get(PotionSaver.class).shouldFreeze(((StatusEffectInstance) (Object) this).getEffectType().value())) {
            info.cancel();
        }
    }
}
