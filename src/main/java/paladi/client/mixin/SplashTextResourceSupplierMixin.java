/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.systems.config.Config;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Random;

@Mixin(SplashTextResourceSupplier.class)
public abstract class SplashTextResourceSupplierMixin {
    @Unique
    private boolean override = true;
    @Unique
    private static final Random random = new Random();
    @Unique
    private final List<String> PaladiSplashes = getPaladiSplashes();

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void onApply(CallbackInfoReturnable<SplashTextRenderer> cir) {
        if (Config.get() == null || !Config.get().titleScreenSplashes.get()) return;

        if (override) cir.setReturnValue(new SplashTextRenderer(Text.literal(PaladiSplashes.get(random.nextInt(PaladiSplashes.size())))));
        override = !override;
    }

    @Unique
    private static List<String> getPaladiSplashes() {
        return List.of(
                "Paladi on Crack!",
                "Star Paladi Client on GitHub!",
                "Based utility mod.",
                "§6MineGame159 §fbased god",
                "§4PaladiClient.com",
                "§4Paladi on Crack!",
                "§6Paladi on Crack!"
        );
    }

}
