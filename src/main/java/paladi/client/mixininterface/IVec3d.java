/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixininterface;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.joml.Vector3d;

@SuppressWarnings("UnusedReturnValue")
public interface IVec3d {
    Vec3d Paladi$set(double x, double y, double z);

    default Vec3d Paladi$set(Vec3i vec) {
        return Paladi$set(vec.getX(), vec.getY(), vec.getZ());
    }

    default Vec3d Paladi$set(Vector3d vec) {
        return Paladi$set(vec.x, vec.y, vec.z);
    }

    default Vec3d Paladi$set(Vec3d pos) {
        return Paladi$set(pos.x, pos.y, pos.z);
    }

    Vec3d Paladi$setXZ(double x, double z);

    Vec3d Paladi$setY(double y);
}
