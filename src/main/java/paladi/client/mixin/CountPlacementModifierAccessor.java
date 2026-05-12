package paladi.client.mixin;

import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.util.math.intprovider.IntProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CountPlacementModifier.class)
public interface CountPlacementModifierAccessor {
    @Accessor("count")
    IntProvider getCount();
}
