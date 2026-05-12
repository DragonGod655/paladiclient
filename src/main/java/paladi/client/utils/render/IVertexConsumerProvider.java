/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.render;

import net.minecraft.client.render.VertexConsumerProvider;

public interface IVertexConsumerProvider extends VertexConsumerProvider {
    void setOffset(int offsetX, int offsetY, int offsetZ);
}
