/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.world.Timer;
import net.minecraft.client.render.RenderTickCounter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.Dynamic.class)
public abstract class RenderTickCounterDynamicMixin {
    @Shadow
    private float dynamicDeltaTicks;

    @Inject(method = "beginRenderTick(J)I", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/RenderTickCounter$Dynamic;lastTimeMillis:J", opcode = Opcodes.PUTFIELD))
    private void onBeingRenderTick(long a, CallbackInfoReturnable<Integer> info) {
        Modules modules = Modules.get();
        if (modules != null) {
            Timer timer = modules.get(Timer.class);
            if (timer != null) {
                dynamicDeltaTicks *= (float) timer.getMultiplier();
            }
        }
    }
}
