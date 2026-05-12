/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixininterface;

import net.minecraft.client.gl.Framebuffer;

public interface IWorldRenderer {
    void Paladi$pushEntityOutlineFramebuffer(Framebuffer framebuffer);

    void Paladi$popEntityOutlineFramebuffer();
}
