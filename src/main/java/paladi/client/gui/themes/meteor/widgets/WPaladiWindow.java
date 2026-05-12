/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.themes.Paladi.PaladiGuiTheme;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.WWidget;
import paladi.client.gui.widgets.containers.WWindow;
import paladi.client.utils.render.color.Color;

public class WPaladiWindow extends WWindow implements PaladiWidget {
    public WPaladiWindow(WWidget icon, String title) {
        super(icon, title);
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new WPaladiHeader(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (expanded || animProgress > 0) {
            PaladiGuiTheme theme = theme();

            // ── Panel background: deep navy ─────────────────────────────
            Color panelBg = new Color(8, 10, 20, 220);
            renderer.quad(x, y + header.height, width, height - header.height, panelBg);

            // ── Subtle left edge accent line on panel ───────────────────
            Color accent = theme.accentColor.get();
            renderer.quad(x, y + header.height, theme.scale(1), height - header.height,
                new Color(accent.r, accent.g, accent.b, 60));

            // ── Bottom glow line ────────────────────────────────────────
            renderer.quad(x, y + height - theme.scale(1), width, theme.scale(1),
                new Color(accent.r, accent.g, accent.b, 45));
        }
    }

    private class WPaladiHeader extends WHeader {
        public WPaladiHeader(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            PaladiGuiTheme theme = theme();
            Color accent = theme.accentColor.get();

            // ── Header background: dark navy base ──────────────────────
            Color headerBase = new Color(10, 12, 24, 245);
            renderer.quad(x, y, width, height, headerBase);

            // ── Left gradient: accent → transparent ────────────────────
            // Left 35% has accent fade
            double gradW = width * 0.38;
            renderer.quad(x, y, (int)gradW, height,
                new Color(accent.r, accent.g, accent.b, 90),
                new Color(accent.r, accent.g, accent.b, 0));

            // ── Top accent line ─────────────────────────────────────────
            renderer.quad(x, y, width, theme.scale(1),
                new Color(accent.r, accent.g, accent.b, 180));

            // ── Bottom bright accent line (separator) ───────────────────
            renderer.quad(x, y + height - theme.scale(1.5), width, theme.scale(1.5),
                new Color(accent.r, accent.g, accent.b, 200));
        }
    }
}
