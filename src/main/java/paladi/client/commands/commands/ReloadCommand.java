/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import paladi.client.commands.Command;
import paladi.client.renderer.Fonts;
import paladi.client.systems.Systems;
import paladi.client.systems.friends.Friend;
import paladi.client.systems.friends.Friends;
import paladi.client.utils.network.Capes;
import paladi.client.utils.network.PaladiExecutor;
import net.minecraft.command.CommandSource;

public class ReloadCommand extends Command {
    public ReloadCommand() {
        super("reload", "Reloads many systems.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            warning("Reloading systems, this may take a while.");

            Systems.load();
            Capes.init();
            Fonts.refresh();
            PaladiExecutor.execute(() -> Friends.get().forEach(Friend::updateInfo));

            return SINGLE_SUCCESS;
        });
    }
}
