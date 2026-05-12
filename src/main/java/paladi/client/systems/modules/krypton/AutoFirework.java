package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;

public class AutoFirework extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> requireJump = sgGeneral.add(new BoolSetting.Builder()
        .name("require-space-key")
        .description("Chỉ tự động bắn pháo khi bạn đang ĐÈ phím Space (Nhảy). Giúp kiểm soát tốt hơn.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("use-delay-ticks")
        .description("Độ trễ giữa các lần tự động bắn pháo (20 ticks = 1s).")
        .defaultValue(30)
        .min(1)
        .sliderMax(60)
        .build()
    );

    private final Setting<Boolean> searchInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("search-full-inventory")
        .description("Tự động kiếm pháo trong toàn bộ túi đồ đưa xuống Hotbar (Anti-cheat bypass).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> strictAntiCheat = sgGeneral.add(new BoolSetting.Builder()
        .name("strict-anti-cheat")
        .description("Giữ pháo ở tay lâu hơn 1 chút trước khi cất (vượt mặt anti-cheat khó).")
        .defaultValue(true)
        .build()
    );

    private int tickCounter = 0;
    private int swapBackTimer = 0;
    private boolean isHoldingFirework = false;

    public AutoFirework() {
        super(Categories.MISC, "auto-firework", "Tự kiếm pháo trong túi và tự bắn khi bay Elytra.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        swapBackTimer = 0;
        isHoldingFirework = false;
    }

    @Override
    public void onDeactivate() {
        if (isHoldingFirework) {
            InvUtils.swapBack();
            isHoldingFirework = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // 1. Xử lý swap back nếu dùng strict anti-cheat
        if (isHoldingFirework) {
            if (swapBackTimer > 0) {
                swapBackTimer--;
                return; // Đang giữ pháo ở tay thì không làm gì khác
            } else {
                InvUtils.swapBack();
                isHoldingFirework = false;
            }
        }

        // 2. Kiểm tra điều kiện bay và phím Space
        if (!mc.player.isGliding()) {
            tickCounter = delay.get(); // Để sẵn sàng bắn ngay khi bắt đầu bay
            return;
        }

        if (requireJump.get() && !mc.options.jumpKey.isPressed()) {
            return; // Đang bay nhưng không đè Space thì không bắn
        }

        // 3. Đếm delay chống spam pháo
        tickCounter++;
        if (tickCounter < delay.get()) return;

        // 4. Tìm pháo hoa
        FindItemResult firework = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        
        // Nếu không có ở hotbar nhưng cho phép tìm trong túi đồ
        if (!firework.found() && searchInventory.get()) {
            FindItemResult invFirework = InvUtils.find(Items.FIREWORK_ROCKET);
            if (invFirework.found()) {
                // Di chuyển pháo từ túi đồ xuống ô hotbar số 9 (slot 8)
                InvUtils.move().from(invFirework.slot()).toHotbar(8);
                firework = InvUtils.findInHotbar(Items.FIREWORK_ROCKET); // Tìm lại sau khi chuyển
            } else {
                return; // Hết pháo trong người
            }
        }

        // 5. Bắn pháo
        if (firework.found()) {
            if (strictAntiCheat.get()) {
                // Đổi tay, dùng, rồi giữ 2 tick trước khi đổi lại
                InvUtils.swap(firework.slot(), true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                isHoldingFirework = true;
                swapBackTimer = 3; // Giữ pháo ở tay 3 tick cho server ghi nhận
            } else {
                // Gửi packet đổi nhanh (dễ bị detect ở server gắt)
                InvUtils.swap(firework.slot(), true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                InvUtils.swapBack();
            }
            tickCounter = 0;
        }
    }
}
