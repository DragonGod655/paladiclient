/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.misc.text;

import paladi.client.utils.render.color.Color;

/**
 * Encapsulates a string and the color it should have. See {@link TextUtils}
 */
public record ColoredText(String text, Color color) {
}
