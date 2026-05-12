/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixininterface;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public interface IRaycastContext {
    void Paladi$set(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, RaycastContext.FluidHandling fluidHandling, Entity entity);
}
