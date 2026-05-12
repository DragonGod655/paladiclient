/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.render.NoRender;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true)
    private void modifyPos(BlockPos pos, CallbackInfoReturnable<Vec3d> info) {
        if (Modules.get() == null) return;

        if (Modules.get().get(NoRender.class).noTextureRotations()) info.setReturnValue(Vec3d.ZERO);
    }
}
