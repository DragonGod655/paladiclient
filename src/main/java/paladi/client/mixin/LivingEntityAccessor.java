/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker("swimUpward")
    void Paladi$swimUpwards(TagKey<Fluid> fluid);

    @Accessor("jumping")
    boolean Paladi$isJumping();

    @Accessor("jumpingCooldown")
    int Paladi$getJumpCooldown();

    @Accessor("jumpingCooldown")
    void Paladi$setJumpCooldown(int cooldown);
}
