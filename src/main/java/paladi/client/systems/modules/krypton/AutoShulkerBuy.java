package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.block.ShulkerBoxBlock;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;

public class AutoShulkerBuy extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> buyCommand = sgGeneral.add(new StringSetting.Builder()
        .name("buy-command")
        .description("Lệnh dùng để mua shulker trên server.")
        .defaultValue("/buy shulker")
        .build()
    );

    private final Setting<Integer> minShulkers = sgGeneral.add(new IntSetting.Builder()
        .name("min-shulkers")
        .description("Số lượng shulker tối thiểu trong túi. Nếu ít hơn sẽ tự động mua.")
        .defaultValue(1)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("buy-delay-ticks")
        .description("Độ trễ giữa mỗi lần gửi lệnh mua (20 ticks = 1 giây). Tăng lên để tránh bị kick do spam.")
        .defaultValue(40)
        .min(20)
        .sliderMax(100)
        .build()
    );

    private int timer = 0;

    public AutoShulkerBuy() {
        super(Categories.DONUT, "auto-shulker-buy", "Tự động mua shulker khi gần hết (có delay chống spam kick).");
    }

    @Override
    public void onActivate() {
        timer = delay.get(); // Đợi 1 chút trước khi bắt đầu mua để an toàn
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        int currentShulkers = countShulkers();

        // Nếu số lượng shulker trong người ít hơn mức tối thiểu
        if (currentShulkers < minShulkers.get()) {
            mc.player.networkHandler.sendChatCommand(buyCommand.get().replace("/", ""));
            info("Đã gửi lệnh mua shulker! (Hiện có: " + currentShulkers + ")");
            timer = delay.get(); // Reset timer để tạo delay
        }
    }

    private int countShulkers() {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }
}
