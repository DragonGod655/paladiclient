package paladi.client.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import paladi.client.commands.Command;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.krypton.ChibiDebugger;
import net.minecraft.command.CommandSource;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public class ChibiCommand extends Command {
    public ChibiCommand() {
        super("chibi", "Send a message to your Chibi assistant.", "cb");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("message", greedyString()).executes(context -> {
            String message = getString(context, "message");
            
            // Log to brain
            ChibiDebugger chibi = Modules.get().get(ChibiDebugger.class);
            if (chibi != null) {
                chibi.reportMessage(message);
            }
            
            return SINGLE_SUCCESS;
        }));
    }
}
