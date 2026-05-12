/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.PaladiClient;
import paladi.client.events.render.RenderBlockEntityEvent;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityRenderManagerMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <S extends BlockEntityRenderState> void onRenderEntity(S renderState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState arg, CallbackInfo ci) {
        RenderBlockEntityEvent event = PaladiClient.EVENT_BUS.post(RenderBlockEntityEvent.get(renderState));
        if (event.isCancelled()) ci.cancel();
    }
}
