/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixininterface;

import net.minecraft.item.ItemStack;

public interface IAbstractFurnaceScreenHandler {
    boolean Paladi$isItemSmeltable(ItemStack itemStack);
}
