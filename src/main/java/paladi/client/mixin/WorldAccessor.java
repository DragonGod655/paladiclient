/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(World.class)
public interface WorldAccessor {
    @Invoker("getEntityLookup")
    EntityLookup<Entity> Paladi$getEntityLookup();
}
