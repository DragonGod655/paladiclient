/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixininterface;

import net.minecraft.util.math.BlockPos;

public interface IBox {
    void Paladi$expand(double v);

    void Paladi$set(double x1, double y1, double z1, double x2, double y2, double z2);

    default void Paladi$set(BlockPos pos) {
        Paladi$set(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }
}
