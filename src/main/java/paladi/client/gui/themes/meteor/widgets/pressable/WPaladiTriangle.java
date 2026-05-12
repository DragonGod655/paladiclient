/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets.pressable;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.pressable.WTriangle;

public class WPaladiTriangle extends WTriangle implements PaladiWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.rotatedQuad(x, y, width, height, rotation, GuiRenderer.TRIANGLE, theme().backgroundColor.get(pressed, mouseOver));
    }
}
