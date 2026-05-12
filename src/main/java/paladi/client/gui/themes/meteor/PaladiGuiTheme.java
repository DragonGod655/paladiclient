/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi;

import paladi.client.gui.DefaultSettingsWidgetFactory;
import paladi.client.gui.GuiTheme;
import paladi.client.gui.WidgetScreen;
import paladi.client.gui.renderer.packer.GuiTexture;
import paladi.client.gui.themes.Paladi.widgets.*;
import paladi.client.gui.themes.Paladi.widgets.input.WPaladiDropdown;
import paladi.client.gui.themes.Paladi.widgets.input.WPaladiSlider;
import paladi.client.gui.themes.Paladi.widgets.input.WPaladiTextBox;
import paladi.client.gui.themes.Paladi.widgets.pressable.*;
import paladi.client.gui.utils.AlignmentX;
import paladi.client.gui.utils.CharFilter;
import paladi.client.gui.widgets.*;
import paladi.client.gui.widgets.containers.WSection;
import paladi.client.gui.widgets.containers.WView;
import paladi.client.gui.widgets.containers.WWindow;
import paladi.client.gui.widgets.input.WDropdown;
import paladi.client.gui.widgets.input.WSlider;
import paladi.client.gui.widgets.input.WTextBox;
import paladi.client.gui.widgets.pressable.*;
import paladi.client.renderer.text.TextRenderer;
import paladi.client.settings.*;
import paladi.client.systems.accounts.Account;
import paladi.client.systems.modules.Module;
import paladi.client.utils.render.color.Color;
import paladi.client.utils.render.color.SettingColor;
import net.minecraft.client.util.MacWindowUtil;

import static paladi.client.PaladiClient.mc;

public class PaladiGuiTheme extends GuiTheme {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgTextColors = settings.createGroup("Text");
    private final SettingGroup sgBackgroundColors = settings.createGroup("Background");
    private final SettingGroup sgOutline = settings.createGroup("Outline");
    private final SettingGroup sgSeparator = settings.createGroup("Separator");
    private final SettingGroup sgScrollbar = settings.createGroup("Scrollbar");
    private final SettingGroup sgSlider = settings.createGroup("Slider");
    private final SettingGroup sgStarscript = settings.createGroup("Starscript");

    // General

    public final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the GUI.")
        .defaultValue(1)
        .min(0.75)
        .sliderRange(0.75, 4)
        .onSliderRelease()
        .onChanged(aDouble -> {
            if (mc.currentScreen instanceof WidgetScreen) ((WidgetScreen) mc.currentScreen).invalidate();
        })
        .build()
    );

    public final Setting<AlignmentX> moduleAlignment = sgGeneral.add(new EnumSetting.Builder<AlignmentX>()
        .name("module-alignment")
        .description("How module titles are aligned.")
        .defaultValue(AlignmentX.Left)
        .build()
    );

    public final Setting<Boolean> categoryIcons = sgGeneral.add(new BoolSetting.Builder()
        .name("category-icons")
        .description("Adds item icons to module categories.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> hideHUD = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-HUD")
        .description("Hide HUD when in GUI.")
        .defaultValue(false)
        .onChanged(v -> {
            if (mc.currentScreen instanceof WidgetScreen) mc.options.hudHidden = v;
        })
        .build()
    );

    // Colors

    public final Setting<SettingColor> accentColor = color("accent", "Main color of the GUI.", new SettingColor(0, 210, 255));
    public final Setting<SettingColor> checkboxColor = color("checkbox", "Color of checkbox.", new SettingColor(0, 210, 255));
    public final Setting<SettingColor> plusColor = color("plus", "Color of plus button.", new SettingColor(0, 240, 130));
    public final Setting<SettingColor> minusColor = color("minus", "Color of minus button.", new SettingColor(255, 60, 50));
    public final Setting<SettingColor> favoriteColor = color("favorite", "Color of checked favorite button.", new SettingColor(255, 210, 0));

    // Text

    public final Setting<SettingColor> textColor = color(sgTextColors, "text", "Color of text.", new SettingColor(235, 238, 245));
    public final Setting<SettingColor> textSecondaryColor = color(sgTextColors, "text-secondary-text", "Color of secondary text.", new SettingColor(160, 165, 180));
    public final Setting<SettingColor> textHighlightColor = color(sgTextColors, "text-highlight", "Color of text highlighting.", new SettingColor(0, 210, 255, 80));
    public final Setting<SettingColor> titleTextColor = color(sgTextColors, "title-text", "Color of title text.", new SettingColor(235, 238, 245));
    public final Setting<SettingColor> loggedInColor = color(sgTextColors, "logged-in-text", "Color of logged in account name.", new SettingColor(0, 240, 130));
    public final Setting<SettingColor> placeholderColor = color(sgTextColors, "placeholder", "Color of placeholder text.", new SettingColor(255, 255, 255, 25));

    // Background

    public final ThreeStateColorSetting backgroundColor = new ThreeStateColorSetting(
            sgBackgroundColors,
            "background",
            new SettingColor(8, 10, 20, 220),
            new SettingColor(14, 16, 28, 225),
            new SettingColor(22, 25, 40, 230)
    );

    public final Setting<SettingColor> moduleBackground = color(sgBackgroundColors, "module-background", "Color of module background when active.", new SettingColor(0, 160, 220, 22));

    // Outline

    public final ThreeStateColorSetting outlineColor = new ThreeStateColorSetting(
            sgOutline,
            "outline",
            new SettingColor(20, 25, 42),
            new SettingColor(30, 35, 56),
            new SettingColor(0, 180, 230, 140)
    );

    // Separator

    public final Setting<SettingColor> separatorText = color(sgSeparator, "separator-text", "Color of separator text", new SettingColor(0, 210, 255));
    public final Setting<SettingColor> separatorCenter = color(sgSeparator, "separator-center", "Center color of separators.", new SettingColor(0, 210, 255, 180));
    public final Setting<SettingColor> separatorEdges = color(sgSeparator, "separator-edges", "Color of separator edges.", new SettingColor(0, 120, 160, 80));

    // Scrollbar

    public final ThreeStateColorSetting scrollbarColor = new ThreeStateColorSetting(
            sgScrollbar,
            "Scrollbar",
            new SettingColor(0, 80, 110, 160),
            new SettingColor(0, 140, 180, 180),
            new SettingColor(0, 180, 220, 200)
    );

    // Slider

    public final ThreeStateColorSetting sliderHandle = new ThreeStateColorSetting(
            sgSlider,
            "slider-handle",
            new SettingColor(0, 210, 255),
            new SettingColor(40, 220, 255),
            new SettingColor(80, 230, 255)
    );

    public final Setting<SettingColor> sliderLeft = color(sgSlider, "slider-left", "Color of slider left part.", new SettingColor(0, 180, 230));
    public final Setting<SettingColor> sliderRight = color(sgSlider, "slider-right", "Color of slider right part.", new SettingColor(20, 22, 38));

    // Starscript

    private final Setting<SettingColor> starscriptText = color(sgStarscript, "starscript-text", "Color of text in Starscript code.", new SettingColor(169, 183, 198));
    private final Setting<SettingColor> starscriptBraces = color(sgStarscript, "starscript-braces", "Color of braces in Starscript code.", new SettingColor(150, 150, 150));
    private final Setting<SettingColor> starscriptParenthesis = color(sgStarscript, "starscript-parenthesis", "Color of parenthesis in Starscript code.", new SettingColor(169, 183, 198));
    private final Setting<SettingColor> starscriptDots = color(sgStarscript, "starscript-dots", "Color of dots in starscript code.", new SettingColor(169, 183, 198));
    private final Setting<SettingColor> starscriptCommas = color(sgStarscript, "starscript-commas", "Color of commas in starscript code.", new SettingColor(169, 183, 198));
    private final Setting<SettingColor> starscriptOperators = color(sgStarscript, "starscript-operators", "Color of operators in Starscript code.", new SettingColor(169, 183, 198));
    private final Setting<SettingColor> starscriptStrings = color(sgStarscript, "starscript-strings", "Color of strings in Starscript code.", new SettingColor(106, 135, 89));
    private final Setting<SettingColor> starscriptNumbers = color(sgStarscript, "starscript-numbers", "Color of numbers in Starscript code.", new SettingColor(104, 141, 187));
    private final Setting<SettingColor> starscriptKeywords = color(sgStarscript, "starscript-keywords", "Color of keywords in Starscript code.", new SettingColor(204, 120, 50));
    private final Setting<SettingColor> starscriptAccessedObjects = color(sgStarscript, "starscript-accessed-objects", "Color of accessed objects (before a dot) in Starscript code.", new SettingColor(152, 118, 170));

    public PaladiGuiTheme() {
        super("Paladi");

        settingsFactory = new DefaultSettingsWidgetFactory(this);
    }

    private Setting<SettingColor> color(SettingGroup group, String name, String description, SettingColor color) {
        return group.add(new ColorSetting.Builder()
                .name(name + "-color")
                .description(description)
                .defaultValue(color)
                .build());
    }
    private Setting<SettingColor> color(String name, String description, SettingColor color) {
        return color(sgColors, name, description, color);
    }

    // Widgets

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WPaladiWindow(icon, title));
    }

    @Override
    public WLabel label(String text, boolean title, double maxWidth) {
        if (maxWidth == 0 && !text.contains("\n")) return w(new WPaladiLabel(text, title));
        return w(new WPaladiMultiLabel(text, title, maxWidth));
    }

    @Override
    public WHorizontalSeparator horizontalSeparator(String text) {
        return w(new WPaladiHorizontalSeparator(text));
    }

    @Override
    public WVerticalSeparator verticalSeparator() {
        return w(new WPaladiVerticalSeparator());
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new WPaladiButton(text, texture));
    }

    @Override
    protected WConfirmedButton confirmedButton(String text, String confirmText, GuiTexture texture) {
        return w(new WPaladiConfirmedButton(text, confirmText, texture));
    }

    @Override
    public WMinus minus() {
        return w(new WPaladiMinus());
    }

    @Override
    public WConfirmedMinus confirmedMinus() {
        return w(new WPaladiConfirmedMinus());
    }

    @Override
    public WPlus plus() {
        return w(new WPaladiPlus());
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new WPaladiCheckbox(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return w(new WPaladiSlider(value, min, max));
    }

    @Override
    public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        return w(new WPaladiTextBox(text, placeholder, filter, renderer));
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return w(new WPaladiDropdown<>(values, value));
    }

    @Override
    public WTriangle triangle() {
        return w(new WPaladiTriangle());
    }

    @Override
    public WTooltip tooltip(String text) {
        return w(new WPaladiTooltip(text));
    }

    @Override
    public WView view() {
        return w(new WPaladiView());
    }

    @Override
    public WSection section(String title, boolean expanded, WWidget headerWidget) {
        return w(new WPaladiSection(title, expanded, headerWidget));
    }

    @Override
    public WAccount account(WidgetScreen screen, Account<?> account) {
        return w(new WPaladiAccount(screen, account));
    }

    @Override
    public WWidget module(Module module, String title) {
        return w(new WPaladiModule(module, title));
    }

    @Override
    public WQuad quad(Color color) {
        return w(new WPaladiQuad(color));
    }

    @Override
    public WTopBar topBar() {
        return w(new WPaladiTopBar());
    }

    @Override
    public WFavorite favorite(boolean checked) {
        return w(new WPaladiFavorite(checked));
    }

    // Colors

    @Override
    public Color textColor() {
        return textColor.get();
    }

    @Override
    public Color textSecondaryColor() {
        return textSecondaryColor.get();
    }

    //     Starscript

    @Override
    public Color starscriptTextColor() {
        return starscriptText.get();
    }

    @Override
    public Color starscriptBraceColor() {
        return starscriptBraces.get();
    }

    @Override
    public Color starscriptParenthesisColor() {
        return starscriptParenthesis.get();
    }

    @Override
    public Color starscriptDotColor() {
        return starscriptDots.get();
    }

    @Override
    public Color starscriptCommaColor() {
        return starscriptCommas.get();
    }

    @Override
    public Color starscriptOperatorColor() {
        return starscriptOperators.get();
    }

    @Override
    public Color starscriptStringColor() {
        return starscriptStrings.get();
    }

    @Override
    public Color starscriptNumberColor() {
        return starscriptNumbers.get();
    }

    @Override
    public Color starscriptKeywordColor() {
        return starscriptKeywords.get();
    }

    @Override
    public Color starscriptAccessedObjectColor() {
        return starscriptAccessedObjects.get();
    }

    // Other

    @Override
    public TextRenderer textRenderer() {
        return TextRenderer.get();
    }

    @Override
    public double scale(double value) {
        double scaled = value * scale.get();

        if (MacWindowUtil.IS_MAC) {
            scaled /= (double) mc.getWindow().getWidth() / mc.getWindow().getFramebufferWidth();
        }

        return scaled;
    }

    @Override
    public boolean categoryIcons() {
        return categoryIcons.get();
    }

    @Override
    public boolean hideHUD() {
        return hideHUD.get();
    }

    public class ThreeStateColorSetting {
        private final Setting<SettingColor> normal, hovered, pressed;

        public ThreeStateColorSetting(SettingGroup group, String name, SettingColor c1, SettingColor c2, SettingColor c3) {
            normal = color(group, name, "Color of " + name + ".", c1);
            hovered = color(group, "hovered-" + name, "Color of " + name + " when hovered.", c2);
            pressed = color(group, "pressed-" + name, "Color of " + name + " when pressed.", c3);
        }

        public SettingColor get() {
            return normal.get();
        }

        public SettingColor get(boolean pressed, boolean hovered, boolean bypassDisableHoverColor) {
            if (pressed) return this.pressed.get();
            return (hovered && (bypassDisableHoverColor || !disableHoverColor)) ? this.hovered.get() : this.normal.get();
        }

        public SettingColor get(boolean pressed, boolean hovered) {
            return get(pressed, hovered, false);
        }
    }
}
