/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.widgets;

public abstract class WHorizontalSeparator extends WWidget {
    protected String text;
    protected double textWidth;

    public WHorizontalSeparator(String text) {
        this.text = text;
    }

    @Override
    protected void onCalculateSize() {
        if (text != null) textWidth = theme.textWidth(text);

        width = 1;
        height = text != null ? theme.textHeight() : theme.scale(3);
    }
}
