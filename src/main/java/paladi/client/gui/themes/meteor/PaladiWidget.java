/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.utils.BaseWidget;
import paladi.client.gui.widgets.WWidget;
import paladi.client.utils.render.color.Color;

public interface PaladiWidget extends BaseWidget {
    default PaladiGuiTheme theme() {
        return (PaladiGuiTheme) getTheme();
    }

    default void renderBackground(GuiRenderer renderer, WWidget widget, Color outlineColor, Color backgroundColor) {
        PaladiGuiTheme theme = theme();
        double s = theme.scale(2);

        renderer.quad(widget.x + s, widget.y + s, widget.width - s * 2, widget.height - s * 2, backgroundColor);

        renderer.quad(widget.x, widget.y, widget.width, s, outlineColor);
        renderer.quad(widget.x, widget.y + widget.height - s, widget.width, s, outlineColor);
        renderer.quad(widget.x, widget.y + s, s, widget.height - s * 2, outlineColor);
        renderer.quad(widget.x + widget.width - s, widget.y + s, s, widget.height - s * 2, outlineColor);
    }

    default void renderBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
        PaladiGuiTheme theme = theme();
        renderBackground(renderer, widget, theme.outlineColor.get(pressed, mouseOver), theme.backgroundColor.get(pressed, mouseOver));
    }
}
