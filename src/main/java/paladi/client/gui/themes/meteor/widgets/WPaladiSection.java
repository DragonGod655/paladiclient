/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.WWidget;
import paladi.client.gui.widgets.containers.WSection;
import paladi.client.gui.widgets.pressable.WTriangle;

public class WPaladiSection extends WSection {
    public WPaladiSection(String title, boolean expanded, WWidget headerWidget) {
        super(title, expanded, headerWidget);
    }

    @Override
    protected WHeader createHeader() {
        return new WPaladiHeader(title);
    }

    protected class WPaladiHeader extends WHeader {
        private WTriangle triangle;

        public WPaladiHeader(String title) {
            super(title);
        }

        @Override
        public void init() {
            add(theme.horizontalSeparator(title)).expandX();

            if (headerWidget != null) add(headerWidget);

            triangle = new WHeaderTriangle();
            triangle.theme = theme;
            triangle.action = this::onClick;

            add(triangle);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            triangle.rotation = (1 - animProgress) * -90;
        }
    }

    protected static class WHeaderTriangle extends WTriangle implements PaladiWidget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            renderer.rotatedQuad(x, y, width, height, rotation, GuiRenderer.TRIANGLE, theme().textColor.get());
        }
    }
}
