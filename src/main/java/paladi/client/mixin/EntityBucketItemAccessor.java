/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.item.EntityBucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityBucketItem.class)
public interface EntityBucketItemAccessor {
    @Accessor("entityType")
    EntityType<?> Paladi$getEntityType();
}
