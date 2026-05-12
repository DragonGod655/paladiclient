package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import paladi.client.utils.Utils;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import paladi.client.events.game.OpenScreenEvent;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

public class AutoSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems   = settings.createGroup("Items to Sell");
    private final SettingGroup sgOrder   = settings.createGroup("Order Settings");

    // ──────────────────────────────────────────────
    //  Sell Mode
    // ──────────────────────────────────────────────

    public enum SellMode {
        All,     // 1 lệnh chung: /sell all
        PerItem, // lệnh riêng mỗi item: /sell {item}
        Order    // lệnh order mỗi item: /order {item} {amount}
    }

    private final Setting<SellMode> sellMode = sgGeneral.add(new EnumSetting.Builder<SellMode>()
        .name("sell-mode")
        .description("All = lệnh chung. PerItem = /sell từng item. Order = /order từng item (lời hơn).")
        .defaultValue(SellMode.All)
        .build()
    );

    // ──────────────────────────────────────────────
    //  Lệnh (hiển thị theo mode)
    // ──────────────────────────────────────────────

    private final Setting<String> sellCommand = sgGeneral.add(new StringSetting.Builder()
        .name("sell-command")
        .description("Lệnh sell chung. Vd: /sell all | /sellall | /shop sell all")
        .defaultValue("/sell all")
        .visible(() -> sellMode.get() == SellMode.All)
        .build()
    );

    private final Setting<String> perItemCommandTemplate = sgGeneral.add(new StringSetting.Builder()
        .name("per-item-command")
        .description("Mẫu lệnh PerItem. Dùng {item} = tên item. Vd: /sell {item}")
        .defaultValue("/sell {item}")
        .visible(() -> sellMode.get() == SellMode.PerItem)
        .build()
    );

    // ──────────────────────────────────────────────
    //  Timer chính
    // ──────────────────────────────────────────────

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-seconds")
        .description("Thời gian (giây) giữa mỗi lần sell/order.")
        .defaultValue(30)
        .min(1)
        .sliderMax(120)
        .build()
    );

    private final Setting<Boolean> autoSellOnFull = sgGeneral.add(new BoolSetting.Builder()
        .name("sell-on-full-inv")
        .description("Sell ngay khi inventory đầy, không đợi hết timer.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sellOnlyIfHasItems = sgGeneral.add(new BoolSetting.Builder()
        .name("only-sell-if-has-items")
        .description("Chỉ gửi lệnh khi inventory thực sự có item đó.")
        .defaultValue(true)
        .visible(() -> sellMode.get() != SellMode.All)
        .build()
    );

    // ──────────────────────────────────────────────
    //  Items to Sell (dùng cho PerItem & Order)
    // ──────────────────────────────────────────────

    private final Setting<List<Item>> itemsToSell = sgItems.add(new ItemListSetting.Builder()
        .name("items")
        .description("Danh sách item muốn bán (dùng cho mode PerItem và Order).")
        .defaultValue()
        .visible(() -> sellMode.get() != SellMode.All)
        .build()
    );

    // ──────────────────────────────────────────────
    //  Order Settings
    // ──────────────────────────────────────────────

    private final Setting<String> orderCommandTemplate = sgOrder.add(new StringSetting.Builder()
        .name("order-command")
        .description("Mẫu lệnh Order. Server tự tìm người mua giá cao nhất. Vd: /order {item}  |  Nếu server cần số lượng: /order {item} {amount}")
        .defaultValue("/order {item}")
        .visible(() -> sellMode.get() == SellMode.Order)
        .build()
    );

    private final Setting<Integer> orderCommandDelay = sgOrder.add(new IntSetting.Builder()
        .name("order-cmd-delay-ticks")
        .description("Số tick chờ giữa mỗi lệnh order (tránh bị server kick spam). 20 tick = 1 giây.")
        .defaultValue(10)
        .min(0)
        .sliderMax(40)
        .visible(() -> sellMode.get() == SellMode.Order)
        .build()
    );

    private final Setting<Boolean> orderUseMaxAmount = sgOrder.add(new BoolSetting.Builder()
        .name("order-include-amount")
        .description("Thêm số lượng vào lệnh nếu template có {amount}. Tắt nếu server chỉ cần /order {item}.")
        .defaultValue(false)
        .visible(() -> sellMode.get() == SellMode.Order)
        .build()
    );

    private final Setting<Boolean> autoClickOrderGui = sgOrder.add(new BoolSetting.Builder()
        .name("auto-click-order-gui")
        .description("Tự động click vào ô bán đồ khi bảng Order hiện lên (để bán cho người giá cao nhất).")
        .defaultValue(true)
        .visible(() -> sellMode.get() == SellMode.Order)
        .build()
    );

    private final Setting<Integer> orderClickSlot = sgOrder.add(new IntSetting.Builder()
        .name("order-click-slot")
        .description("Vị trí ô chứa đơn hàng cao nhất trong GUI (Thường là ô 10, 11 hoặc 13).")
        .defaultValue(10)
        .min(0)
        .sliderMax(53)
        .visible(() -> sellMode.get() == SellMode.Order && autoClickOrderGui.get())
        .build()
    );

    // ──────────────────────────────────────────────
    //  State
    // ──────────────────────────────────────────────
    private int tickCounter = 0;

    // Hàng đợi lệnh order để gửi từng cái một (tránh spam)
    private final List<String> orderQueue = new ArrayList<>();
    private int orderTickDelay = 0;

    public AutoSell() {
        super(Categories.DONUT, "auto-sell", "Tự động sell/order item. Hỗ trợ /sell all, /sell item, /order item amount.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        orderQueue.clear();
        orderTickDelay = 0;
        waitingForGui = false;
    }

    private boolean waitingForGui = false;
    private int guiWaitTicks = 0;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Auto Click GUI check
        if (waitingForGui) {
            guiWaitTicks--;
            if (guiWaitTicks <= 0) {
                waitingForGui = false;
            } else if (mc.currentScreen instanceof GenericContainerScreen screen) {
                // Click into the configured slot to fulfill the order
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, orderClickSlot.get(), 0, SlotActionType.PICKUP, mc.player);
                info("Đã auto click bán cho Order cao nhất!");
                mc.player.closeHandledScreen(); // Close after clicking
                waitingForGui = false;
            }
        }

        // ── Xử lý hàng đợi order (gửi từng lệnh có delay) ──
        if (!orderQueue.isEmpty()) {
            if (orderTickDelay > 0) {
                orderTickDelay--;
                return; // chờ đủ delay mới gửi lệnh tiếp theo
            }
            String cmd = orderQueue.remove(0);
            mc.player.networkHandler.sendChatCommand(cmd);
            orderTickDelay = orderCommandDelay.get();
            if (autoClickOrderGui.get()) {
                waitingForGui = true;
                guiWaitTicks = 40; // Đợi tối đa 2 giây cho GUI mở
            }

            if (orderQueue.isEmpty()) {
                info("Đã order xong tất cả item! Tự động tắt module.");
                toggle();
            }
            return; // đang xử lý queue, chưa đếm timer chính
        }

        // ── Timer chính ──
        tickCounter++;

        boolean shouldAct = false;
        if (autoSellOnFull.get() && isInventoryFull()) {
            shouldAct = true;
        }
        if (tickCounter >= delay.get() * 20) {
            shouldAct = true;
            tickCounter = 0;
        }

        if (!shouldAct) return;

        // ── Thực thi theo mode ──
        switch (sellMode.get()) {

            case All -> {
                String cmd = stripSlash(sellCommand.get());
                mc.player.networkHandler.sendChatCommand(cmd);
                info("Đã sell all!");
            }

            case PerItem -> {
                List<Item> toSell = getFilteredItems();
                if (toSell == null) return;
                String template = perItemCommandTemplate.get().trim();
                for (Item item : toSell) {
                    String itemName = getItemName(item);
                    String cmd = stripSlash(template.replace("{item}", itemName));
                    mc.player.networkHandler.sendChatCommand(cmd);
                }
                info("Đã sell " + toSell.size() + " loại item! Tự động tắt.");
                toggle();
            }

            case Order -> {
                List<Item> toOrder = getFilteredItems();
                if (toOrder == null) return;

                String template = orderCommandTemplate.get().trim();
                for (Item item : toOrder) {
                    String itemName = getItemName(item);

                    String resolved = template.replace("{item}", itemName);

                    // {amount} là tùy chọn — chỉ thay thế nếu template có dùng
                    if (resolved.contains("{amount}") && orderUseMaxAmount.get()) {
                        int amount = countItemInInventory(item);
                        if (amount <= 0) continue;
                        resolved = resolved.replace("{amount}", String.valueOf(amount));
                    } else {
                        resolved = resolved.replace("{amount}", "").trim();
                    }

                    String cmd = stripSlash(resolved);
                    orderQueue.add(cmd);
                }

                if (orderQueue.isEmpty()) {
                    info("Không có item nào cần order (hoặc đã hết). Tự động tắt.");
                    toggle();
                } else {
                    info("Bắt đầu order " + orderQueue.size() + " lệnh...");
                    // Gửi lệnh đầu tiên ngay
                    String first = orderQueue.remove(0);
                    mc.player.networkHandler.sendChatCommand(first);
                    orderTickDelay = orderCommandDelay.get();
                    if (autoClickOrderGui.get()) {
                        waitingForGui = true;
                        guiWaitTicks = 40;
                    }
                    
                    if (orderQueue.isEmpty()) {
                        info("Đã gửi lệnh order duy nhất. Tự động tắt.");
                        toggle();
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    /** Lấy danh sách item cần sell, có lọc nếu bật only-sell-if-has-items. Trả null nếu danh sách rỗng. */
    private List<Item> getFilteredItems() {
        List<Item> items = itemsToSell.get();
        if (items.isEmpty()) {
            warning("Chưa chọn item nào! Vào tab 'Items to Sell' để thêm.");
            return null;
        }

        if (!sellOnlyIfHasItems.get()) return new ArrayList<>(items);

        List<Item> result = new ArrayList<>();
        for (Item item : items) {
            if (hasItemInInventory(item)) result.add(item);
        }
        return result.isEmpty() ? null : result;
    }

    /** Kiểm tra inventory đầy (36 slot, kể cả slot không full stack). */
    private boolean isInventoryFull() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) return false;
            if (stack.getCount() < stack.getMaxCount()) return false;
        }
        return true;
    }

    /** Kiểm tra có item trong inventory không (có check trong shulker). */
    private boolean hasItemInInventory(Item item) {
        ItemStack[] shulkerItems = new ItemStack[27];
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == item) return true;
            if (Utils.isShulker(stack.getItem())) {
                Utils.getItemsInContainerItem(stack, shulkerItems);
                for (ItemStack shulkerStack : shulkerItems) {
                    if (shulkerStack != null && shulkerStack.getItem() == item) return true;
                }
            }
        }
        return false;
    }

    /** Đếm tổng số lượng item trong inventory (kể cả trong shulker). */
    private int countItemInInventory(Item item) {
        int count = 0;
        ItemStack[] shulkerItems = new ItemStack[27];
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == item) {
                count += stack.getCount();
            } else if (Utils.isShulker(stack.getItem())) {
                Utils.getItemsInContainerItem(stack, shulkerItems);
                for (ItemStack shulkerStack : shulkerItems) {
                    if (shulkerStack != null && shulkerStack.getItem() == item) {
                        count += shulkerStack.getCount();
                    }
                }
            }
        }
        return count;
    }

    /** Lấy tên item theo registry (vd: "diamond", "iron_ingot"). */
    private String getItemName(Item item) {
        return Registries.ITEM.getId(item).getPath();
    }

    /** Bỏ dấu / ở đầu lệnh nếu có. */
    private String stripSlash(String cmd) {
        cmd = cmd.trim();
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }
}
