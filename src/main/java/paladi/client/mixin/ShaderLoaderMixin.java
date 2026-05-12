/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.renderer.PaladiRenderPipelines;
import net.minecraft.client.gl.ShaderLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderLoader.class)
public abstract class ShaderLoaderMixin {
    @Inject(method = "apply(Lnet/minecraft/client/gl/ShaderLoader$Definitions;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V", at = @At("TAIL"))
    private void Paladi$reloadPipelines(CallbackInfo info) {
        PaladiRenderPipelines.precompile();
    }
}
