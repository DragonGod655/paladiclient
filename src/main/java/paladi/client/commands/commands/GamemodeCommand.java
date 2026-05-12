/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import paladi.client.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.world.GameMode;

public class GamemodeCommand extends Command {
    public GamemodeCommand() {
        super("gamemode", "Changes your gamemode client-side.", "gm");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        for (GameMode gameMode : GameMode.values()) {
            builder.then(literal(gameMode.getId()).executes(context -> {
                mc.interactionManager.setGameMode(gameMode);
                return SINGLE_SUCCESS;
            }));
        }
    }
}
