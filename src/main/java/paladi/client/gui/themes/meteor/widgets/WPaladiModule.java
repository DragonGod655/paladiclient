/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.gui.themes.Paladi.PaladiGuiTheme;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.utils.AlignmentX;
import paladi.client.gui.widgets.pressable.WPressable;
import paladi.client.systems.modules.Module;
import paladi.client.utils.render.color.Color;
import net.minecraft.util.math.MathHelper;

import static paladi.client.PaladiClient.mc;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class WPaladiModule extends WPressable implements PaladiWidget {
    private final Module module;
    private final String title;

    private double titleWidth;
    private double animationProgress1; // active state animation (0..1)
    private double animationProgress2; // hover animation (0..1)

    public WPaladiModule(Module module, String title) {
        this.module = module;
        this.title = title;
        this.tooltip = module.description;

        animationProgress1 = module.isActive() ? 1.0 : 0.0;
        animationProgress2 = 0.0;
    }

    @Override
    public double pad() {
        return theme.scale(5);
    }

    @Override
    protected void onCalculateSize() {
        double pad = pad();
        if (titleWidth == 0) titleWidth = theme.textWidth(title.replace('-', ' ').toUpperCase());
        width  = pad + titleWidth + theme.scale(18) + pad; // extra space for dot
        height = pad + theme.textHeight() + pad;
    }

    @Override
    protected void onPressed(int button) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) module.toggle();
        else if (button == GLFW_MOUSE_BUTTON_RIGHT) mc.setScreen(theme.moduleScreen(module));
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        PaladiGuiTheme theme = theme();
        double pad = pad();
        boolean active = module.isActive();

        // ── Animate active state (0→1) ──────────────────────────────────
        animationProgress1 += (active ? 1.0 : -1.0) * delta * 14.0;
        animationProgress1 = MathHelper.clamp(animationProgress1, 0.0, 1.0);
        double ap = animationProgress1;

        // ── Animate hover state ─────────────────────────────────────────
        animationProgress2 += (mouseOver ? 1.0 : -1.0) * delta * 20.0;
        animationProgress2 = MathHelper.clamp(animationProgress2, 0.0, 1.0);
        double hp = animationProgress2;

        // ── Hover background ────────────────────────────────────────────
        if (hp > 0) {
            renderer.quad(x, y, width, height,
                new Color(255, 255, 255, (int)(12 * hp)));
        }

        // ── Active background glow (cyan tint) ─────────────────────────
        if (ap > 0) {
            Color accent = theme.accentColor.get();
            // Left portion brighter, fades to transparent on right
            renderer.quad(x, y, width / 2.5, height,
                new Color(accent.r, accent.g, accent.b, (int)(35 * ap)),
                new Color(accent.r, accent.g, accent.b, 0));
        }

        // ── Left accent bar (animated, only when active) ────────────────
        if (ap > 0) {
            Color accent = theme.accentColor.get();
            double barW = theme.scale(2.5);
            // Solid accent bar
            renderer.quad(x, y, barW, height,
                new Color(accent.r, accent.g, accent.b, (int)(255 * ap)));
            // Soft glow to the right of bar
            renderer.quad(x + barW, y, barW * 3, height,
                new Color(accent.r, accent.g, accent.b, (int)(40 * ap)),
                new Color(accent.r, accent.g, accent.b, 0));
        }

        // ── Bottom divider line ─────────────────────────────────────────
        renderer.quad(x + theme.scale(6), y + height - theme.scale(0.5),
            width - theme.scale(12), theme.scale(0.5),
            new Color(255, 255, 255, 8));

        // ── Module title text ───────────────────────────────────────────
        String displayTitle = title.replace('-', ' ').toUpperCase();

        // Indent slightly when active bar is visible
        double xText = x + pad + (ap > 0 ? theme.scale(3.5) : 0);

        if (theme.moduleAlignment.get() == AlignmentX.Center) {
            xText = x + (width - theme.textWidth(displayTitle)) / 2.0 + (ap > 0 ? theme.scale(1.5) : 0);
        } else if (theme.moduleAlignment.get() == AlignmentX.Right) {
            xText = x + width - pad - theme.scale(18) - theme.textWidth(displayTitle);
        }

        // Text: full white when active, dimmer when inactive
        int textAlpha = active ? 255 : (int)(160 + 40 * hp); // dims when not active, brightens on hover
        Color txtColor = new Color(
            theme.textColor.get().r,
            theme.textColor.get().g,
            theme.textColor.get().b,
            textAlpha
        );
        renderer.text(displayTitle, xText, y + (height - theme.textHeight()) / 2.0, txtColor, false);

        // ── Right toggle indicator (Pill shape) ─────────────────────────
        double toggleWidth  = theme.scale(16);
        double toggleHeight = theme.scale(8);
        double toggleX      = x + width - pad - toggleWidth;
        double toggleY      = y + (height - toggleHeight) / 2.0;

        // Background color transitions from dark grey to accent color
        Color accent = theme.accentColor.get();
        Color bgInactive = new Color(42, 48, 60, 255);
        
        int bgR = (int) MathHelper.lerp(ap, bgInactive.r, accent.r);
        int bgG = (int) MathHelper.lerp(ap, bgInactive.g, accent.g);
        int bgB = (int) MathHelper.lerp(ap, bgInactive.b, accent.b);
        Color bgColor = new Color(bgR, bgG, bgB, 255);

        // Draw pill background
        double radius = toggleHeight / 2.0;
        renderer.quad(toggleX + radius, toggleY, toggleWidth - toggleHeight, toggleHeight, bgColor);
        // Left and right rounded caps
        renderer.quad(toggleX, toggleY, toggleHeight, toggleHeight, GuiRenderer.CIRCLE, bgColor);
        renderer.quad(toggleX + toggleWidth - toggleHeight, toggleY, toggleHeight, toggleHeight, GuiRenderer.CIRCLE, bgColor);

        // Draw the circular knob inside the pill
        double knobSize = toggleHeight - theme.scale(2); // 1 pixel padding on top/bottom
        double knobPadding = theme.scale(1);
        
        // Knob moves from left to right based on animation progress
        double knobMinX = toggleX + knobPadding;
        double knobMaxX = toggleX + toggleWidth - knobSize - knobPadding;
        double knobX = MathHelper.lerp(ap, knobMinX, knobMaxX);
        double knobY = toggleY + knobPadding;

        // Draw shadow for knob (optional premium touch)
        renderer.quad(knobX, knobY + theme.scale(0.5), knobSize, knobSize, GuiRenderer.CIRCLE, new Color(0, 0, 0, 100));
        // Draw the knob itself
        renderer.quad(knobX, knobY, knobSize, knobSize, GuiRenderer.CIRCLE, new Color(255, 255, 255, 255));
    }
}
