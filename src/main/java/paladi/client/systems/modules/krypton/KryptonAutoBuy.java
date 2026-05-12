package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static paladi.client.PaladiClient.mc;

public class KryptonAutoBuy extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> itemsToBuy = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Danh sách item bạn muốn mua.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Boolean> buyAll = sgGeneral.add(new BoolSetting.Builder()
        .name("buy-all")
        .description("Mua tất cả các item dưới giá tối đa (Cẩn thận!).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> buyPotions = sgGeneral.add(new BoolSetting.Builder()
        .name("buy-potions")
        .description("Tự động mua mọi loại Potion dưới giá tối đa.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxPrice = sgGeneral.add(new IntSetting.Builder()
        .name("max-price")
        .description("Giá tối đa bạn chấp nhận mua.")
        .defaultValue(1000)
        .min(1)
        .sliderMax(1000000)
        .build()
    );

    private final Setting<String> priceKeywords = sgGeneral.add(new StringSetting.Builder()
        .name("price-keywords")
        .description("Từ khóa để tìm giá trong lore (phân cách bằng dấu phẩy).")
        .defaultValue("Giá:, Price:, Cost:, Bán:")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("buy-delay-ticks")
        .description("Delay giữa các lần click (để tránh bị kick).")
        .defaultValue(10)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> autoConfirm = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-confirm")
        .description("Tự động click xác nhận nếu có bảng confirm.")
        .defaultValue(true)
        .build()
    );

    private int timer = 0;

    public KryptonAutoBuy() {
        super(Categories.DONUT, "krypton-auto-buy", "Tự động mua item trong Shop hoặc AH.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

        if (timer > 0) {
            timer--;
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        // Thường thì phần trên của GUI là Shop
        int containerSize = 0;
        if (handler instanceof GenericContainerScreenHandler) {
            containerSize = handler.getRows() * 9;
        }

        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            // Ưu tiên click nút xác nhận trước nếu đang hiện bảng confirm
            if (autoConfirm.get() && isConfirmButton(stack)) {
                clickSlot(screen, i);
                timer = delay.get();
                return;
            }

            // Chỉ tìm item để mua trong phần container (phần trên), tránh click nhầm đồ trong túi
            if (containerSize > 0 && i < containerSize) {
                boolean shouldBuy = buyAll.get()
                    || itemsToBuy.get().contains(stack.getItem())
                    || (buyPotions.get() && stack.getItem() instanceof net.minecraft.item.PotionItem);

                if (shouldBuy) {
                    long price = parsePrice(stack);
                    if (price != -1 && price <= maxPrice.get()) {
                        clickSlot(screen, i);
                        timer = delay.get();
                        return;
                    }
                }
            }
        }
    }

    private void clickSlot(GenericContainerScreen screen, int slot) {
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private boolean isConfirmButton(ItemStack stack) {
        String name = stack.getName().getString().toLowerCase();
        // Các từ khóa phổ biến cho nút xác nhận
        return name.contains("xác nhận") || name.contains("confirm") || name.contains("accept") || name.contains("đồng ý") || name.contains("click to buy");
    }

    private long parsePrice(ItemStack stack) {
        // Kiểm tra trong tên item (một số server để giá ở tên)
        String itemName = stack.getName().getString().toLowerCase();
        for (String kw : priceKeywords.get().split(",")) {
            String trimmedKw = kw.trim().toLowerCase();
            if (!trimmedKw.isEmpty() && itemName.contains(trimmedKw)) {
                long price = extractNumber(itemName);
                if (price != -1) return price;
            }
        }

        // Kiểm tra trong Lore
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return -1;

        String[] keywords = priceKeywords.get().split(",");
        for (Text text : lore.lines()) {
            String line = text.getString().toLowerCase();
            for (String kw : keywords) {
                String trimmedKw = kw.trim().toLowerCase();
                if (!trimmedKw.isEmpty() && line.contains(trimmedKw)) {
                    long price = extractNumber(line);
                    if (price != -1) return price;
                }
            }
        }

        return -1;
    }

    private long extractNumber(String text) {
        // Loại bỏ dấu phân cách nghìn và lấy số
        String cleaned = text.replaceAll("[,.]", "");
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(cleaned);
        
        long lastMatch = -1;
        while (m.find()) {
            try {
                lastMatch = Long.parseLong(m.group());
            } catch (NumberFormatException ignored) {}
        }
        return lastMatch;
    }
}
