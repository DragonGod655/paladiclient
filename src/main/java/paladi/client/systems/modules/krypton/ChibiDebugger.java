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

    public enum VoiceStyle {
        JapaneseAnime("Giọng Anime Nhật Cute (Nanami)", "ja-JP-NanamiNeural"),
        Vietnamese("Giọng Tiếng Việt (Hoài My)", "vi-VN-HoaiMyNeural");

        private final String title;
        public final String voiceCode;

        VoiceStyle(String title, String voiceCode) {
            this.title = title;
            this.voiceCode = voiceCode;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final Setting<VoiceStyle> voiceStyle = sgGeneral.add(new EnumSetting.Builder<VoiceStyle>()
        .name("voice-style")
        .description("Lựa chọn giọng nói lồng tiếng cho Chibi (Anime Nhật Cute / Tiếng Việt).")
        .defaultValue(VoiceStyle.JapaneseAnime)
        .build()
    );

    private final Setting<Boolean> launchDesktopCompanion = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-launch-desktop")
        .description("Tự động chạy Chibi Desktop Companion ở chế độ ngầm (không hiện cửa sổ Terminal).")
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
        respondToUser(msg);
    }

    public void respondToUser(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;

        // Đảm bảo Chibi AI & Voice Server (chibi_ai.py) đang chạy ẩn ngầm
        ensureAIServerRunning();

        // Log to brain
        try (FileWriter fw = new FileWriter("chibi_brain.txt", true)) {
            fw.write("[" + java.time.LocalDateTime.now() + "] " + msg + "\n");
        } catch (IOException ignored) {}

        // Gửi request bất đồng bộ đến Chibi AI Server (Gemini AI + Giọng nói Edge-TTS phát ra loa)
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("http://127.0.0.1:5000/chat");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(7000);

                String jsonInputString = "{\"message\": \"" + escapeJson(msg) + "\", \"voice\": \"" + voiceStyle.get().voiceCode + "\"}";
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                if (conn.getResponseCode() == 200) {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder responseStr = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            responseStr.append(line.trim());
                        }
                        String responseText = parseJsonResponse(responseStr.toString());
                        if (responseText != null && !responseText.isEmpty()) {
                            onAIResponseReceived(responseText);
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // Dự phòng nếu AI Server đang khởi động
            onAIResponseReceived(getFallbackResponse(msg));
        }).start();
    }

    private void onAIResponseReceived(String responseText) {
        // Thông báo cho HUD Bubble
        for (paladi.client.systems.hud.HudElement element : paladi.client.systems.hud.Hud.get()) {
            if (element instanceof paladi.client.systems.hud.elements.ChibiWaifuHud chibi) {
                chibi.setMessage(responseText);
            }
        }

        // Nhắn vào Chat game
        if (notifyChat.get()) {
            ChatUtils.sendMsg(Text.literal(Formatting.LIGHT_PURPLE + "(◕‿◕) " + Formatting.WHITE + responseText));
        }

        // Phát hiệu ứng âm thanh Allay trong game
        if (mc.player != null && mc.world != null) {
            mc.world.playSound(mc.player, mc.player.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_ALLAY_ITEM_TAKEN,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
    }

    private String getFallbackResponse(String msg) {
        String lower = msg.toLowerCase().trim();
        if (lower.contains("chào") || lower.contains("hi") || lower.contains("hello") || lower.contains("ơi")) {
            return "Dạ, Chibi nghe đây ạ! Chúc chủ nhân một ngày chơi game thật vui vẻ! (◕‿◕)";
        } else if (lower.contains("?") || lower.contains("sao") || lower.contains("gì") || lower.contains("thế nào") || lower.contains("đâu")) {
            return "Em đang phát AI & giọng nói cho chủ nhân đây ạ! (◕‿◕)";
        } else if (lower.contains("xây") || lower.contains("litematica") || lower.contains("block")) {
            return "Bật EasyPlacePlus và Litematica Layer HUD lên để xây chuẩn 100% nhé chủ nhân! (^▽^)";
        }
        return "Dạ, Chibi luôn ở đây đồng hành và hỗ trợ chủ nhân nè! (^▽^)";
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private String parseJsonResponse(String json) {
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("response")) {
                return obj.get("response").getAsString();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean desktopLaunched = false;

    private void ensureAIServerRunning() {
        try {
            // Check if AI server is already running on port 5000
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", 5000)) {
                return; // Already running!
            } catch (IOException ignored) {}

            java.io.File script = new java.io.File("c:\\Users\\Admin\\Documents\\GitHub\\meteor-client\\chibi_ai.py");
            if (!script.exists() && mc.runDirectory != null) {
                script = new java.io.File(mc.runDirectory, "chibi_ai.py");
            }
            if (script.exists()) {
                new ProcessBuilder("pythonw", script.getAbsolutePath())
                    .directory(script.getParentFile())
                    .start();
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        String msg = event.message;
        if (msg != null && msg.toLowerCase().contains("chibi")) {
            respondToUser(msg);
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
        if (launchDesktopCompanion.get()) {
            launchDesktopSilently();
        }
        ensureAIServerRunning();
    }

    private void launchDesktopSilently() {
        if (desktopLaunched) return;
        desktopLaunched = true;
        try {
            java.io.File script = new java.io.File("c:\\Users\\Admin\\Documents\\GitHub\\meteor-client\\desktop_companion.py");
            if (!script.exists() && mc.runDirectory != null) {
                script = new java.io.File(mc.runDirectory, "desktop_companion.py");
            }
            if (script.exists()) {
                new ProcessBuilder("pythonw", script.getAbsolutePath())
                    .directory(script.getParentFile())
                    .start();
            }
        } catch (Throwable ignored) {}
    }
}
