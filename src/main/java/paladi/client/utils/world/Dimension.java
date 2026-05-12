/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.world;

public enum Dimension {
    Overworld,
    Nether,
    End;

    public Dimension opposite() {
        return switch (this) {
            case Overworld -> Nether;
            case Nether -> Overworld;
            default -> this;
        };
    }
}
