/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.renderer;

import paladi.client.utils.misc.Pool;
import paladi.client.utils.render.color.Color;

public abstract class GuiRenderOperation<T extends GuiRenderOperation<T>> {
    protected double x, y;
    protected Color color;

    public void set(double x, double y, Color color) {
        this.x = x;
        this.y = y;
        this.color = color;
    }

    @SuppressWarnings("unchecked")
    public void run(Pool<T> pool) {
        onRun();
        pool.free((T) this);
    }

    protected abstract void onRun();
}
