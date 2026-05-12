/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.PaladiClient;
import paladi.client.events.render.ApplyTransformationEvent;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Transformation.class)
public abstract class TransformationMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true)
    private void onApply(boolean leftHanded, MatrixStack.Entry entry, CallbackInfo info) {
        ApplyTransformationEvent event = PaladiClient.EVENT_BUS.post(ApplyTransformationEvent.get((Transformation) (Object) this, leftHanded));
        if (event.isCancelled()) info.cancel();
    }
}
