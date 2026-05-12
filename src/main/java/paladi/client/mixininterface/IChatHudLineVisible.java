/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixininterface;

public interface IChatHudLineVisible extends IChatHudLine {
    boolean Paladi$isStartOfEntry();
    void Paladi$setStartOfEntry(boolean start);
}
