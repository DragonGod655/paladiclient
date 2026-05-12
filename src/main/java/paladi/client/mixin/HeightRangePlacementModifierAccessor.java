package paladi.client.mixin;

import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HeightRangePlacementModifier.class)
public interface HeightRangePlacementModifierAccessor {
    @Accessor("height")
    HeightProvider getHeight();
}
