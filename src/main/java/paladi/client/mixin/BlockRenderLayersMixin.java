/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.render.Xray;
import paladi.client.systems.modules.world.Ambience;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderLayers.class)
public class BlockRenderLayersMixin {
    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private static void onGetBlockLayer(BlockState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        if (Modules.get() == null) return;

        int alpha = Xray.getAlpha(state, null);
        if (alpha > 0 && alpha < 255) cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
    }

    @Inject(method = "getFluidLayer", at = @At("HEAD"), cancellable = true)
    private static void onGetFluidLayer(FluidState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        if (Modules.get() == null) return;

        int alpha = Xray.getAlpha(state.getBlockState(), null);
        if (alpha > 0 && alpha < 255) {
            cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
        }

        else {
            Ambience ambience = Modules.get().get(Ambience.class);
            int a = ambience.lavaColor.get().a;
            if (ambience.isActive() && ambience.customLavaColor.get() && a > 0 && a < 255) {
                cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
            }
        }
    }
}
