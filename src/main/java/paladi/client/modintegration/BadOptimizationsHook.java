/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.modintegration;

import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.render.Fullbright;
import paladi.client.systems.modules.render.Xray;

import java.util.function.BooleanSupplier;

/*
 * Hook for BadOptimizations mod compatibility.
 * Signals when the lightmap needs to be updated due to Fullbright or Xray state changes.
 */
public class BadOptimizationsHook implements BooleanSupplier {
    private int lastState;

    @Override
    public boolean getAsBoolean() {
        Modules m = Modules.get();
        if (m == null) return false;

        int state = (m.get(Fullbright.class).getGamma() ? 1 : 0) | (m.isActive(Xray.class) ? 2 : 0);
        boolean changed = state != lastState;
        lastState = state;
        return changed;
    }
}
