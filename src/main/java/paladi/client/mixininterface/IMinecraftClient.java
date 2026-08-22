/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixininterface;

import net.minecraft.client.gl.Framebuffer;

public interface IMinecraftClient {
    void Paladi$rightClick();

    void Paladi$setItemUseCooldown(int cooldown);

    void Paladi$setFramebuffer(Framebuffer framebuffer);
}
