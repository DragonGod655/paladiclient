package paladi.client.systems.modules.krypton;

import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.events.game.SendMessageEvent;
import paladi.client.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Formatting;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import paladi.client.events.packets.PacketEvent;
import paladi.client.PaladiClient;
import net.minecraft.text.Text;

public class ChibiDebugger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> notifyChat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notifications")
        .description("Chibi will talk to you in chat when errors occur.")
        .defaultValue(true)
        .build()
    );

    public ChibiDebugger() {
        super(Categories.MISC, "chibi-assistant", "Your cute AI assistant that tracks bugs and helps Antigravity.");
    }

    public void reportError(String moduleName, Exception e) {
        if (!isActive()) return;

        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();

        try (FileWriter fw = new FileWriter("chibi_errors.txt", true)) {
            fw.write("--- Error in " + moduleName + " ---\n");
            fw.write(exceptionAsString + "\n\n");
        } catch (IOException ignored) {}

        if (notifyChat.get()) {
            ChatUtils.sendMsg(Text.literal(Formatting.LIGHT_PURPLE + "(◕‿◕) " + Formatting.WHITE + "Chủ nhân ơi! Có lỗi ở " + Formatting.YELLOW + moduleName + Formatting.WHITE + ". Em đã ghi vào chibi_errors.txt rồi ạ!"));
        }

        // Notify HUD
        for (paladi.client.systems.hud.HudElement element : paladi.client.systems.hud.Hud.get()) {
            if (element instanceof paladi.client.systems.hud.elements.ChibiWaifuHud chibi) {
                chibi.setMessage("(>_<) Có lỗi ở " + moduleName + "!");
            }
        }
    }

    public void reportMessage(String msg) {
        // Log to brain for Antigravity to read later
        try (FileWriter fw = new FileWriter("chibi_brain.txt", true)) {
            fw.write("[" + java.time.LocalDateTime.now() + "] " + msg + "\n");
        } catch (IOException ignored) {}
        
        // Notify HUD
        for (paladi.client.systems.hud.HudElement element : paladi.client.systems.hud.Hud.get()) {
            if (element instanceof paladi.client.systems.hud.elements.ChibiWaifuHud chibi) {
                chibi.setMessage("Đã ghi nhận yêu cầu của chủ nhân!");
            }
        }

        if (msg.contains("?")) {
            ChatUtils.sendMsg(Text.literal(Formatting.LIGHT_PURPLE + "(◕‿◕) " + Formatting.WHITE + "Câu hỏi này khó quá... Để em hỏi anh Antigravity rồi cập nhật cho ngài nhé! (◕‿◕)"));
        }
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        String msg = event.message;
        if (msg.toLowerCase().contains("chibi")) {
            // Log to brain for Antigravity to read later
            try (FileWriter fw = new FileWriter("chibi_brain.txt", true)) {
                fw.write("[" + java.time.LocalDateTime.now() + "] " + msg + "\n");
            } catch (IOException ignored) {}
            
            // Fast response if it's a question
            if (msg.contains("?")) {
                ChatUtils.sendMsg(Text.literal(Formatting.LIGHT_PURPLE + "(◕‿◕) " + Formatting.WHITE + "Câu hỏi này khó quá... Để em hỏi anh Antigravity rồi cập nhật cho ngài ở bản build sau nhé! (◕‿◕)"));
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;
        if (p.getStatus() != 35) return; // 35 is Totem Pop
        
        if (mc.world != null && p.getEntity(mc.world) == mc.player) {
            // Notify HUD
            for (paladi.client.systems.hud.HudElement element : paladi.client.systems.hud.Hud.get()) {
                if (element instanceof paladi.client.systems.hud.elements.ChibiWaifuHud chibi) {
                    chibi.setMessage("Suýt thì bay màu rồi chủ nhân ơi! (>_<)");
                }
            }

            if (notifyChat.get()) {
                ChatUtils.sendMsg(Text.literal(Formatting.LIGHT_PURPLE + "(◕‿◕) " + Formatting.WHITE + "Phùuu... May mà có Totem không là chủ nhân tèo rồi! Cố lên nhé! (◕‿◕)"));
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Chibi idle animations could go here
    }

    @Override
    public void onActivate() {
        info(Formatting.LIGHT_PURPLE + "(^▽^) " + Formatting.WHITE + "Chibi Assistant đã sẵn sàng phục vụ chủ nhân!");
    }
}
