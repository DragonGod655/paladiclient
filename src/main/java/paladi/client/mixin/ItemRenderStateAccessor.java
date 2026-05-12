/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderState.class)
public interface ItemRenderStateAccessor {
    @Accessor("layerCount")
    int Paladi$getLayerCount();

    @Accessor("layers")
    ItemRenderState.LayerRenderState[] Paladi$getLayers();
}
