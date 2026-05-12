/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.screen.MountScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MountScreenHandler.class)
public interface MountScreenHandlerAccessor {
    @Accessor("mount")
    LivingEntity Paladi$getMount();
}
