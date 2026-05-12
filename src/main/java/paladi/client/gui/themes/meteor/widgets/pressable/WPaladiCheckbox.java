/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets.pressable;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.themes.Paladi.PaladiGuiTheme;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.pressable.WCheckbox;
import net.minecraft.util.math.MathHelper;

public class WPaladiCheckbox extends WCheckbox implements PaladiWidget {
    private double animProgress;

    public WPaladiCheckbox(boolean checked) {
        super(checked);
        animProgress = checked ? 1 : 0;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        PaladiGuiTheme theme = theme();

        animProgress += (checked ? 1 : -1) * delta * 14;
        animProgress = MathHelper.clamp(animProgress, 0, 1);

        renderBackground(renderer, this, pressed, mouseOver);

        if (animProgress > 0) {
            double cs = (width - theme.scale(2)) / 1.75 * animProgress;
            renderer.quad(x + (width - cs) / 2, y + (height - cs) / 2, cs, cs, theme.checkboxColor.get());
        }
    }
}
