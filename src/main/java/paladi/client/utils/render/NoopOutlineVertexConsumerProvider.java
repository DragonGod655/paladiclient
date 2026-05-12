/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.render;

import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;

public class NoopOutlineVertexConsumerProvider extends OutlineVertexConsumerProvider {
    public static final NoopOutlineVertexConsumerProvider INSTANCE = new NoopOutlineVertexConsumerProvider();

    private NoopOutlineVertexConsumerProvider() {
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return NoopVertexConsumer.INSTANCE;
    }

    @Override
    public void draw() {
    }
}
