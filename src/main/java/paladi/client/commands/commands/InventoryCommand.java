/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import paladi.client.commands.Command;
import paladi.client.commands.arguments.PlayerArgumentType;
import paladi.client.utils.Utils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.command.CommandSource;

public class InventoryCommand extends Command {
    public InventoryCommand() {
        super("inventory", "Allows you to see parts of another player's inventory.", "inv", "invsee");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("player", PlayerArgumentType.create()).executes(context -> {
            Utils.screenToOpen = new InventoryScreen(PlayerArgumentType.get(context));
            return SINGLE_SUCCESS;
        }));
    }
}
