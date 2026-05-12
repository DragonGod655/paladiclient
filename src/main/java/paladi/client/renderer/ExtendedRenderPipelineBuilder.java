/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import paladi.client.mixininterface.IRenderPipeline;

public class ExtendedRenderPipelineBuilder extends RenderPipeline.Builder {
    private boolean lineSmooth;

    public ExtendedRenderPipelineBuilder(RenderPipeline.Snippet... snippets) {
        for (RenderPipeline.Snippet snippet : snippets) {
            withSnippet(snippet);
        }
    }

    public ExtendedRenderPipelineBuilder withLineSmooth() {
        lineSmooth = true;
        return this;
    }

    @Override
    public RenderPipeline build() {
        RenderPipeline pipeline = super.build();
        ((IRenderPipeline) pipeline).Paladi$setLineSmooth(lineSmooth);

        return pipeline;
    }
}
