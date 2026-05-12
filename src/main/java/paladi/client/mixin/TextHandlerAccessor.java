/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import net.minecraft.client.font.TextHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextHandler.class)
public interface TextHandlerAccessor {
    @Accessor("widthRetriever")
    TextHandler.WidthRetriever Paladi$getWidthRetriever();
}
