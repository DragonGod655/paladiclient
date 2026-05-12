package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class FakeStats extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> fakeMoney = sgGeneral.add(new StringSetting.Builder()
        .name("fake-money").description("Số tiền ảo hiển thị trên Scoreboard.").defaultValue("999.99M").build()
    );

    private final Setting<String> fakeShards = sgGeneral.add(new StringSetting.Builder()
        .name("fake-shards").description("Số Shards ảo hiển thị.").defaultValue("5000").build()
    );

    private final Setting<String> fakeKills = sgGeneral.add(new StringSetting.Builder()
        .name("fake-kills").description("Số Kills ảo.").defaultValue("999").build()
    );

    private final Setting<String> fakeDeaths = sgGeneral.add(new StringSetting.Builder()
        .name("fake-deaths").description("Số Deaths ảo.").defaultValue("0").build()
    );

    private final Setting<String> fakeKeyall = sgGeneral.add(new StringSetting.Builder()
        .name("fake-keyall").description("Thời gian Keyall ảo.").defaultValue("1m 00s").build()
    );

    private final Setting<String> fakePlaytime = sgGeneral.add(new StringSetting.Builder()
        .name("fake-playtime").description("Thời gian chơi ảo.").defaultValue("999d 23h").build()
    );

    public FakeStats() {
        super(Categories.DONUT, "fake-stats", "Tự do chỉnh sửa các thông số hiển thị trên Scoreboard (Money, Shards, ...).");
    }

    private String pendingFakeValue = null;
    private long lastPendingTime = 0;

    public String replaceText(String text) {
        if (text == null || !isActive()) return text;
        
        long now = System.currentTimeMillis();

        // 1. Kiểm tra nếu có giá trị Fake đang chờ thay thế cho Text Node tiếp theo
        if (pendingFakeValue != null && now - lastPendingTime < 5) {
            // Đảm bảo Text Node này là con số/giá trị (chứa số và các ký tự cơ bản)
            if (text.length() < 30 && text.matches("^[§0-9a-fk-orA-Za-z\\s.,]+$") && text.matches(".*[0-9].*")) {
                String replacement = pendingFakeValue;
                pendingFakeValue = null; // Đã thay thế xong
                return replacement;
            }
        }

        // 2. Xử lý các từ khoá
        if (text.contains("Money")) return processKeyword(text, "Money", fakeMoney.get(), now);
        if (text.contains("Shards")) return processKeyword(text, "Shards", fakeShards.get(), now);
        if (text.contains("Kills")) return processKeyword(text, "Kills", fakeKills.get(), now);
        if (text.contains("Deaths")) return processKeyword(text, "Deaths", fakeDeaths.get(), now);
        if (text.contains("Keyall")) return processKeyword(text, "Keyall", fakeKeyall.get(), now);
        if (text.contains("Playtime")) return processKeyword(text, "Playtime", fakePlaytime.get(), now);
        
        return text;
    }

    private String processKeyword(String text, String keyword, String fakeValue, long now) {
        // Regex kiểm tra xem trong cùng chuỗi này có chứa số (giá trị) phía sau từ khoá hay không
        String regex = "(" + keyword + "[^0-9]*)[0-9].*";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher m = p.matcher(text);
        
        if (m.find()) {
            // Giá trị nằm ngay trong chuỗi này! (VD: "Kills §c32")
            // Ta giữ nguyên phần màu sắc phía trước ($1) và chèn fakeValue vào
            return text.replaceAll(regex, "$1" + fakeValue);
        } else {
            // Giá trị KHÔNG nằm trong chuỗi này (VD: chuỗi chỉ là "Kills ").
            // Game sẽ render giá trị ở Text Node tiếp theo.
            // Ta lưu fakeValue lại để chờ Text Node tiếp theo và thay thế nó.
            pendingFakeValue = fakeValue;
            lastPendingTime = now;
            return text; // Giữ nguyên chuỗi "Kills "
        }
    }
}
