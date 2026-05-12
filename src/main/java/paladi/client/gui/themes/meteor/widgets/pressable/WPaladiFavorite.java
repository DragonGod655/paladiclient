/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets.pressable;

import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.pressable.WFavorite;
import paladi.client.utils.render.color.Color;

public class WPaladiFavorite extends WFavorite implements PaladiWidget {
    public WPaladiFavorite(boolean checked) {
        super(checked);
    }

    @Override
    protected Color getColor() {
        return theme().favoriteColor.get();
    }
}
