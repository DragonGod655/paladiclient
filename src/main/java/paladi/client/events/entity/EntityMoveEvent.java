/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.events.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class EntityMoveEvent {
    private static final EntityMoveEvent INSTANCE = new EntityMoveEvent();

    public Entity entity;
    public Vec3d movement;

    public static EntityMoveEvent get(Entity entity, Vec3d movement) {
        INSTANCE.entity = entity;
        INSTANCE.movement = movement;
        return INSTANCE;
    }
}
