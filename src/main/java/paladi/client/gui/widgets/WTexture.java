/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.widgets;

import paladi.client.gui.renderer.GuiRenderer;
import paladi.client.renderer.Texture;

public class WTexture extends WWidget {
    private final double width, height;
    private final double rotation;
    private final Texture texture;

    public WTexture(double width, double height, double rotation, Texture texture) {
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.texture = texture;
    }

    @Override
    protected void onCalculateSize() {
        super.width = theme.scale(width);
        super.height = theme.scale(height);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.texture(x, y, super.width, super.height, rotation, texture);
    }
}
