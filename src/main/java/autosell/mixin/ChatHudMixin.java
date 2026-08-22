package autosell.mixin;

import autosell.AutoSellMod;
import autosell.LicenseManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
    private void onAddMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        if (message == null) return;
        String text = message.getString();
        
        // Tránh loop tin nhắn nội bộ của mod
        if (text.contains("[" + AutoSellMod.MOD_NAME + "]")) {
            return;
        }

        // Bắt tin nhắn bán thành công từ Server game để gửi thống kê lên web
        String lowerText = text.toLowerCase();
        boolean isSaleInfo = lowerText.contains("bán thành công") || lowerText.contains("ban thanh cong") 
                || lowerText.contains("sold") || lowerText.contains("earned") || lowerText.contains("đơn hàng")
                || lowerText.contains("bought your");

        // Nhận diện tin nhắn tiền thuần túy khi đang đợi phản hồi từ server (ví dụ: "$ 20.1K" không đi kèm chữ)
        if (!isSaleInfo && AutoSellMod.active && AutoSellMod.waitingForSaleResponse) {
            String clean = text.replaceAll("(?i)\u00A7[0-9a-fk-or]", "").trim();
            if ((clean.contains("$") || clean.contains("₫") || clean.contains("đ") || clean.toLowerCase().contains("vnđ")) 
                    && !clean.contains(":") && clean.matches(".*\\d.*")) {
                isSaleInfo = true;
                AutoSellMod.waitingForSaleResponse = false; // Tắt cờ khi đã nhận được
            }
        }

        if (isSaleInfo) {
            
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.world != null) {
                String world = mc.world.getRegistryKey().getValue().toString();
                String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer";
                
                // Gửi ngầm tin nhắn chat lên license server để cập nhật thống kê doanh thu thực tế
                LicenseManager.updateClientState(
                    mc.player.getX(), 
                    mc.player.getY(), 
                    mc.player.getZ(), 
                    world, 
                    server, 
                    text
                );

                // Cập nhật thống kê HUD trong game
                AutoSellMod.recordSale(text);
            }
        }
    }
}
