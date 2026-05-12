/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.commands.arguments;

import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.util.math.Direction;

public class DirectionArgumentType extends EnumArgumentType<Direction> {
    private static final DirectionArgumentType INSTANCE = new DirectionArgumentType();

    private DirectionArgumentType() {
        super(Direction.CODEC, Direction::values);
    }

    public static DirectionArgumentType create() {
        return INSTANCE;
    }
}
