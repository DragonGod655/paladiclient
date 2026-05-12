/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import net.minecraft.client.texture.MapTextureManager;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MapTextureManager.class)
public interface MapTextureManagerAccessor {
    @Invoker("getMapTexture")
    MapTextureManager.MapTexture Paladi$invokeGetMapTexture(MapIdComponent id, MapState state);
}
