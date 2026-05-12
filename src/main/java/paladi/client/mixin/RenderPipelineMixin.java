/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import paladi.client.mixininterface.IRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderPipeline.class)
public abstract class RenderPipelineMixin implements IRenderPipeline {
    @Unique
    private boolean lineSmooth;

    @Override
    public void Paladi$setLineSmooth(boolean lineSmooth) {
        this.lineSmooth = lineSmooth;
    }

    @Override
    public boolean Paladi$getLineSmooth() {
        return lineSmooth;
    }
}
