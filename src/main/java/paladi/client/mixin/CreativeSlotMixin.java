/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.mixininterface.ISlot;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen$CreativeSlot")
public abstract class CreativeSlotMixin implements ISlot {
    @Shadow @Final Slot slot;

    @Override
    public int Paladi$getId() {
        return slot.id;
    }

    @Override
    public int Paladi$getIndex() {
        return slot.getIndex();
    }
}
