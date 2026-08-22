package autosell.mixin;

import autosell.AutoSellMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void onSendChatCommand(String command, CallbackInfo ci) {
        String cmdLower = command.trim().toLowerCase(Locale.ROOT);
        if (cmdLower.startsWith(".sellpro") || cmdLower.startsWith(".as")
                || cmdLower.startsWith("sellpro") || cmdLower.startsWith("as")) {
            String normalized = cmdLower.startsWith(".") ? command.trim() : "." + command.trim();
            AutoSellMod.handleCommand(normalized);
            ci.cancel();
        }
    }

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String message, CallbackInfo ci) {
        String msgLower = message.trim().toLowerCase(Locale.ROOT);
        if (msgLower.startsWith(".sellpro") || msgLower.startsWith(".as")
                || msgLower.startsWith("sellpro") || msgLower.startsWith("as")) {
            String normalized = msgLower.startsWith(".") ? message.trim() : "." + message.trim();
            AutoSellMod.handleCommand(normalized);
            ci.cancel();
        }
    }
}
