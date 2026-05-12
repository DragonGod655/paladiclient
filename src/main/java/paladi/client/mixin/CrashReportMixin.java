/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import paladi.client.PaladiClient;
import paladi.client.systems.hud.Hud;
import paladi.client.systems.hud.HudElement;
import paladi.client.systems.hud.elements.TextHud;
import paladi.client.systems.modules.Category;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Modules;
import net.minecraft.util.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(CrashReport.class)
public abstract class CrashReportMixin {
    @Inject(method = "addDetails", at = @At("TAIL"))
    private void onAddDetails(StringBuilder sb, CallbackInfo info) {
        sb.append("\n\n-- Paladi Client --\n\n");
        sb.append("Version: ").append(PaladiClient.VERSION).append("\n");
        if (!PaladiClient.BUILD_NUMBER.isEmpty()) {
            sb.append("Build: ").append(PaladiClient.BUILD_NUMBER).append("\n");
        }

        if (Modules.get() != null) {
            boolean modulesActive = false;
            for (Category category : Modules.loopCategories()) {
                List<Module> modules = Modules.get().getGroup(category);
                boolean categoryActive = false;

                for (Module module : modules) {
                    if (module == null || !module.isActive()) continue;

                    if (!modulesActive) {
                        modulesActive = true;
                        sb.append("\n[[ Active Modules ]]\n");
                    }

                    if (!categoryActive) {
                        categoryActive = true;
                        sb.append("\n[")
                          .append(category)
                          .append("]:\n");
                    }

                    sb.append(module.name).append("\n");
                }

            }

        }

        if (Hud.get() != null && Hud.get().active) {
            boolean hudActive = false;
            for (HudElement element : Hud.get()) {
                if (element == null || !element.isActive()) continue;

                if (!hudActive) {
                    hudActive = true;
                    sb.append("\n[[ Active Hud Elements ]]\n");
                }

                if (!(element instanceof TextHud textHud)) sb.append(element.info.name).append("\n");
                else {
                    sb.append("Text\n{")
                      .append(textHud.text.get())
                      .append("}\n");
                    if (textHud.shown.get() != TextHud.Shown.Always) {
                        sb.append("(")
                          .append(textHud.shown.get())
                          .append(textHud.condition.get())
                          .append(")\n");
                    }
                }
            }
        }
    }
}
