/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemGroups.class)
public interface ItemGroupsAccessor {
    @Accessor("INVENTORY")
    static RegistryKey<ItemGroup> Paladi$getInventory() {
        throw new AssertionError();
    }
}
