/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.mixininterface.IText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Text.class)
public interface TextMixin extends IText {
    @Override
    default void Paladi$invalidateCache() {}
}
