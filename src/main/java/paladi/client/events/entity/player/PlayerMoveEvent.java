/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.events.entity.player;

import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;

public class PlayerMoveEvent {
    private static final PlayerMoveEvent INSTANCE = new PlayerMoveEvent();

    public MovementType type;
    public Vec3d movement;

    public static PlayerMoveEvent get(MovementType type, Vec3d movement) {
        INSTANCE.type = type;
        INSTANCE.movement = movement;
        return INSTANCE;
    }
}
