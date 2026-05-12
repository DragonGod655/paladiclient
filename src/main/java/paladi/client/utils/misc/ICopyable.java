/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.misc;

public interface ICopyable<T extends ICopyable<T>> {
    T set(T value);

    T copy();
}
