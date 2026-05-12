/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.events.entity.player;

import net.minecraft.util.hit.HitResult;

public class ItemUseCrosshairTargetEvent {
    private static final ItemUseCrosshairTargetEvent INSTANCE = new ItemUseCrosshairTargetEvent();

    public HitResult target;

    public static ItemUseCrosshairTargetEvent get(HitResult target) {
        INSTANCE.target = target;
        return INSTANCE;
    }
}
