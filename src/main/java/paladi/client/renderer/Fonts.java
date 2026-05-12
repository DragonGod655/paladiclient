/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.renderer;

import paladi.client.PaladiClient;
import paladi.client.events.Paladi.CustomFontChangedEvent;
import paladi.client.gui.WidgetScreen;
import paladi.client.renderer.text.CustomTextRenderer;
import paladi.client.renderer.text.FontFace;
import paladi.client.renderer.text.FontFamily;
import paladi.client.renderer.text.FontInfo;
import paladi.client.systems.config.Config;
import paladi.client.utils.PreInit;
import paladi.client.utils.render.FontUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static paladi.client.PaladiClient.mc;

public class Fonts {
    public static final String[] BUILTIN_FONTS = { "JetBrains Mono", "Comfortaa", "Tw Cen MT", "Pixelation" };

    public static String DEFAULT_FONT_FAMILY;
    public static FontFace DEFAULT_FONT;

    public static final List<FontFamily> FONT_FAMILIES = new ArrayList<>();
    public static CustomTextRenderer RENDERER;

    private Fonts() {
    }

    @PreInit
    public static void refresh() {
        FONT_FAMILIES.clear();

        for (String builtinFont : BUILTIN_FONTS) {
            FontUtils.loadBuiltin(FONT_FAMILIES, builtinFont);
        }

        for (String fontPath : FontUtils.getSearchPaths()) {
            FontUtils.loadSystem(FONT_FAMILIES, new File(fontPath));
        }

        FONT_FAMILIES.sort(Comparator.comparing(FontFamily::getName));

        PaladiClient.LOG.info("Found {} font families.", FONT_FAMILIES.size());

        DEFAULT_FONT_FAMILY = FontUtils.getBuiltinFontInfo(BUILTIN_FONTS[1]).family();
        DEFAULT_FONT = getFamily(DEFAULT_FONT_FAMILY).get(FontInfo.Type.Regular);

        Config config = Config.get();
        load(config != null ? config.font.get() : DEFAULT_FONT);
    }

    public static void load(FontFace fontFace) {
        if (RENDERER != null) {
            if (RENDERER.fontFace.equals(fontFace)) return;
            else RENDERER.destroy();
        }

        try {
            RENDERER = new CustomTextRenderer(fontFace);
            PaladiClient.EVENT_BUS.post(CustomFontChangedEvent.get());
        }
        catch (Exception e) {
            if (fontFace.equals(DEFAULT_FONT)) {
                throw new RuntimeException("Failed to load default font: " + fontFace, e);
            }

            PaladiClient.LOG.error("Failed to load font: {}", fontFace, e);
            load(Fonts.DEFAULT_FONT);
        }

        if (mc.currentScreen instanceof WidgetScreen && Config.get().customFont.get()) {
            ((WidgetScreen) mc.currentScreen).invalidate();
        }
    }

    public static FontFamily getFamily(String name) {
        for (FontFamily fontFamily : Fonts.FONT_FAMILIES) {
            if (fontFamily.getName().equalsIgnoreCase(name)) {
                return fontFamily;
            }
        }

        return null;
    }
}
