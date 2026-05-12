/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.events.entity.player;

import net.minecraft.item.ItemStack;

public class PickItemsEvent {
    private static final PickItemsEvent INSTANCE = new PickItemsEvent();

    public ItemStack itemStack;
    public int count;

    public static PickItemsEvent get(ItemStack itemStack, int count) {
        INSTANCE.itemStack = itemStack;
        INSTANCE.count = count;
        return INSTANCE;
    }
}
