/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.events.world;

public class AmbientOcclusionEvent {
    private static final AmbientOcclusionEvent INSTANCE = new AmbientOcclusionEvent();

    public float lightLevel = -1;

    public static AmbientOcclusionEvent get() {
        INSTANCE.lightLevel = -1;
        return INSTANCE;
    }
}
