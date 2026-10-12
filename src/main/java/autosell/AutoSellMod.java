package autosell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AutoSellMod implements ClientModInitializer {
    public static final String MOD_NAME = "Auto Sell Pro Vip";
    private static MinecraftClient mc;
    private static KeyBinding toggleKeyBind;
    public static boolean active = false;
    public static boolean waitingForSaleResponse = false;
    public static boolean blockReconnect = false;
    private static boolean wasInWorld = false;

    // GUI Mode States
    private enum SellState {
        IDLE,
        SENDING_COMMAND,
        WAITING_GUI,
        MOVING_ITEMS,
        CLICKING_SELL,
        WAITING_AFTER_SELL,
        WAITING_INVENTORY_FULL_NO_GUI
    }

    private static SellState state = SellState.IDLE;
    private static long delayUntilMs = 0;
    private static long nextAllowedSellTimeMs = 0;
    private static long guiWaitDeadlineMs = 0;
    private static int guiRetries = 0;
    private static int lastSyncId = -1;
    private static long cooldownUntilMs = 0;
    private static int lastCooldownSaleMilestone = 0;

    // Coordinate protection states
    private static double startX = 0;
    private static double startY = 0;
    private static double startZ = 0;
    private static boolean hasStartPos = false;

    // All Mode States
    private static int tickCounter = 0;
    private static int antiSpamCooldown = 0;
    private static boolean justClickedSell = false;
    private static int checkTicks = 0;

    // Item counter for stats
    private static final Map<net.minecraft.item.Item, Integer> sessionSoldItems = new HashMap<>();

    // ──── Thống kê doanh thu trong game ────
    private static double sessionTotalEarned = 0.0;
    private static int sessionSellCount = 0;
    private static long sessionStartTimeMs = 0;
    private static boolean showHud = true;
    private static final Pattern MONEY_PATTERN = Pattern.compile("([\\d.,]+)\\s*([KMBkmb]?)");

    // Key bind press detection state
    private static boolean wasKeyPressed = false;

    @Override
    public void onInitializeClient() {
        mc = MinecraftClient.getInstance();

        // Load config
        Config.load();

        // Register default key bind using standard KeyBinding Category enum
        toggleKeyBind = new KeyBinding(
                "key.autosell.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KeyBinding.Category.MISC
        );

        info("Đã khởi tạo mod độc lập thành công. Mặc định phím F8 để Bật/Tắt.");
    }

    public static KeyBinding getToggleKeyBind() {
        return toggleKeyBind;
    }

    public static void toggle() {
        active = !active;
        if (active) {
            blockReconnect = false; // Reset cờ chặn khi bật lại AutoSell

            // info(Formatting.GREEN + "Bật AutoSell!");
            resetStats();
            tickCounter = 999999;
            antiSpamCooldown = 0;
            justClickedSell = false;
            checkTicks = 0;
            sessionSoldItems.clear();

            if (mc.player != null) {
                startX = mc.player.getX();
                startY = mc.player.getY();
                startZ = mc.player.getZ();
                hasStartPos = true;
            } else {
                hasStartPos = false;
            }

            state = (mc.currentScreen instanceof GenericContainerScreen)
                    ? SellState.MOVING_ITEMS
                    : SellState.WAITING_INVENTORY_FULL_NO_GUI;
            delayUntilMs = 0;
            nextAllowedSellTimeMs = 0;
            guiRetries = 0;
            lastSyncId = -1;
            cooldownUntilMs = 0;
            lastCooldownSaleMilestone = 0;
        } else {
            // info(Formatting.RED + "Tắt AutoSell!");
            state = SellState.IDLE;
            cooldownUntilMs = 0;
        }
    }

    public static void onTick() {
        boolean inWorld = (mc.player != null && mc.world != null);
        if (wasInWorld && !inWorld) {
            // Vừa ngắt kết nối khỏi server/thế giới
            resetStats();
            active = false;
        }
        wasInWorld = inWorld;

        if (!inWorld) return;

        // Handle keybind toggling manually using Window object signature
        boolean isPressed = InputUtil.isKeyPressed(mc.getWindow(), toggleKeyBind.getDefaultKey().getCode());
        if (isPressed && !wasKeyPressed) {
            toggle();
        }
        wasKeyPressed = isPressed;

        if (!active) return;

        // Bảo vệ tài khoản: Kiểm tra người chơi trong danh sách blacklist
        checkPlayerBlacklistProtection();

        // 1. Tọa độ bảo vệ (Coordinates Protection)
        if (Config.get().autoCoordinatesEnabled && hasStartPos) {
            double dx = mc.player.getX() - startX;
            double dy = mc.player.getY() - startY;
            double dz = mc.player.getZ() - startZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > Config.get().positionThreshold) {
                warning("Người chơi đã di chuyển, ngừng bán.");
                toggle();
                return;
            }
        }

        // 1.5. Kiểm tra thời gian nghỉ sau khi đạt mốc 100 sales (tắt đợi 10s rồi sell tiếp)
        if (cooldownUntilMs > 0) {
            long remainingMs = cooldownUntilMs - System.currentTimeMillis();
            if (remainingMs > 0) {
                if (mc.player != null && mc.currentScreen instanceof GenericContainerScreen) {
                    mc.player.closeHandledScreen();
                }
                return;
            } else {
                cooldownUntilMs = 0;
                info("Đã hết " + Config.get().pauseDurationSeconds + "s nghỉ! Gửi lệnh sell tiếp.");
                if (isInventoryFull()) {
                    if (Config.get().sellMode.equalsIgnoreCase("Gui")) {
                        state = SellState.SENDING_COMMAND;
                    } else {
                        mc.player.networkHandler.sendChatCommand(Config.get().sellCommand);
                        antiSpamCooldown = Math.max(100, Config.get().sellCommandDelayTicks);
                    }
                } else {
                    state = SellState.WAITING_INVENTORY_FULL_NO_GUI;
                }
            }
        }

        // 2. Chế độ GUI Mode
        if (Config.get().sellMode.equalsIgnoreCase("Gui")) {
            if (System.currentTimeMillis() < delayUntilMs) return;

            // Nếu rương bị đóng khi đang dọn đồ -> quay về chờ đầy túi
            if ((state == SellState.MOVING_ITEMS || state == SellState.CLICKING_SELL || state == SellState.WAITING_AFTER_SELL)
                    && !(mc.currentScreen instanceof GenericContainerScreen)) {
                state = SellState.WAITING_INVENTORY_FULL_NO_GUI;
            }

            switch (state) {
                case SENDING_COMMAND -> {
                    sessionSoldItems.clear();
                    if (mc.currentScreen instanceof GenericContainerScreen screen) {
                        lastSyncId = screen.getScreenHandler().syncId;
                    } else {
                        lastSyncId = -1;
                    }
                    mc.player.networkHandler.sendChatCommand(Config.get().sellCommand);
                    guiWaitDeadlineMs = System.currentTimeMillis() + 2000;
                    state = SellState.WAITING_GUI;
                }
                case WAITING_GUI -> {
                    if (mc.currentScreen instanceof GenericContainerScreen screen) {
                        String title = screen.getTitle().getString().toLowerCase();
                        if (title.contains(Config.get().guiTitleKeyword.toLowerCase())
                                && screen.getScreenHandler().syncId != lastSyncId) {
                            guiRetries = 0;
                            setDelay(Config.get().moveDelayTicks * 50);
                            state = SellState.MOVING_ITEMS;
                            return;
                        }
                    }
                    if (System.currentTimeMillis() > guiWaitDeadlineMs) {
                        guiRetries++;
                        if (guiRetries >= 3) {
                            guiRetries = 0;
                            nextAllowedSellTimeMs = System.currentTimeMillis() + 3000;
                            state = SellState.WAITING_INVENTORY_FULL_NO_GUI;
                        } else {
                            state = SellState.SENDING_COMMAND;
                        }
                    }
                }
                case MOVING_ITEMS -> {
                    if (System.currentTimeMillis() < nextAllowedSellTimeMs) return;
                    if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                        state = SellState.WAITING_INVENTORY_FULL_NO_GUI;
                        return;
                    }
                    GenericContainerScreenHandler handler = screen.getScreenHandler();
                    int size = handler.slots.size();
                    if (size <= 36) {
                        state = SellState.SENDING_COMMAND;
                        return;
                    }

                    int containerSize = handler.getRows() * 9;
                    int maxAllowed = (handler.getRows() - 1) * 9;
                    int targetChestSlots = Math.min(Config.get().targetChestSlots, maxAllowed);

                    // 1. Đếm số ô đã có đồ trong các ô chứa của rương (tối đa 45 ô đầu)
                    int chestFilledSlots = 0;
                    for (int i = 0; i < targetChestSlots; i++) {
                        if (!handler.getSlot(i).getStack().isEmpty()) {
                            chestFilledSlots++;
                        }
                    }

                    // 2. Chuyển đồ từ túi đồ của người chơi vào rương
                    for (int i = containerSize; i < size; i++) {
                        if (chestFilledSlots >= targetChestSlots) break;

                        ItemStack stack = handler.getSlot(i).getStack();
                        if (!stack.isEmpty() && !stack.isDamageable()) {
                            net.minecraft.item.Item item = stack.getItem();
                            int count = stack.getCount();
                            sessionSoldItems.put(item, sessionSoldItems.getOrDefault(item, 0) + count);

                            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                            handler.getSlot(i).setStack(ItemStack.EMPTY);
                            chestFilledSlots++;
                        }
                    }

                    // 3. Kiểm tra xem rương đã đủ 45 ô chưa
                    if (chestFilledSlots >= targetChestSlots) {
                        setDelay(Config.get().sellDelayTicks * 50);
                        state = SellState.CLICKING_SELL;
                    } else {
                        // Chưa đủ 45 ô -> giữ rương mở, chờ đồ tiếp tục rơi vào túi đồ
                        setDelay(Config.get().moveDelayTicks * 50);
                        state = SellState.MOVING_ITEMS;
                    }
                }
                case CLICKING_SELL -> {
                    if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                        state = SellState.WAITING_INVENTORY_FULL_NO_GUI;
                        return;
                    }

                    StringBuilder itemDetails = new StringBuilder("Sold: ");
                    if (sessionSoldItems.isEmpty()) {
                        itemDetails.append("none");
                    } else {
                        List<String> parts = new ArrayList<>();
                        for (Map.Entry<net.minecraft.item.Item, Integer> entry : sessionSoldItems.entrySet()) {
                            String itemName = entry.getKey().getName().getString();
                            parts.add(entry.getValue() + "x " + itemName);
                        }
                        itemDetails.append(String.join(", ", parts));
                    }

                    GenericContainerScreenHandler handler = screen.getScreenHandler();
                    int targetSlot = Config.get().sellSlot;
                    if (handler.slots.size() > targetSlot) {
                        // info("Đang click ô Bán (Slot " + targetSlot + ")...");
                        LicenseManager.syncSession(
                                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                                mc.world.getRegistryKey().getValue().toString(),
                                mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : sec("23080206080c2f1f041a1717"),
                                "click-slot-" + targetSlot + " | " + itemDetails
                        );
                        waitingForSaleResponse = true; // Kích hoạt cờ đợi phản hồi tiền tệ từ Server
                        mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                    } else {
                        warning("Không tìm thấy ô Bán (Slot " + targetSlot + ")!");
                    }

                    sessionSoldItems.clear();
                    setDelay(Math.max(100, Config.get().sellDelayTicks * 50));
                    state = SellState.WAITING_AFTER_SELL;
                }
                case WAITING_AFTER_SELL -> {
                    checkSalesCooldown();
                    nextAllowedSellTimeMs = System.currentTimeMillis() + 1000;
                    if (mc.player != null && mc.currentScreen instanceof GenericContainerScreen) {
                        mc.player.closeHandledScreen();
                    }
                    state = SellState.WAITING_INVENTORY_FULL_NO_GUI;
                }
                case WAITING_INVENTORY_FULL_NO_GUI -> {
                    boolean readyToSell = System.currentTimeMillis() >= nextAllowedSellTimeMs;
                    boolean shouldAct = readyToSell && isInventoryFull();
                    if (shouldAct) {
                        if (mc.currentScreen instanceof GenericContainerScreen) {
                            state = SellState.MOVING_ITEMS;
                        } else {
                            setDelay(Config.get().sellCommandDelayTicks * 50);
                            state = SellState.SENDING_COMMAND;
                        }
                    }
                }
            }
            return;
        }

        // 3. Chế độ All Mode (Bằng Chat Command)
        if (antiSpamCooldown > 0) {
            antiSpamCooldown--;
            return;
        }

        tickCounter++;
        boolean shouldAct = false;
        if (isInventoryFull()) {
            shouldAct = true;
            tickCounter = 0;
        }

        int targetDelayTicks = Config.get().delay * 20;
        int delayJitter = (int) (Math.random() * 5 - 2);
        targetDelayTicks = Math.max(1, targetDelayTicks + delayJitter);

        if (tickCounter >= targetDelayTicks) {
            shouldAct = true;
            tickCounter = 0;
        }

        if (!shouldAct) return;

        if (Config.get().sellMode.equalsIgnoreCase("All")) {
            waitingForSaleResponse = true; // Kích hoạt cờ đợi phản hồi tiền tệ từ Server
            mc.player.networkHandler.sendChatCommand(Config.get().sellCommand);
            LicenseManager.updateClientState(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    mc.world.getRegistryKey().getValue().toString(),
                    mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer",
                    "cmd=" + Config.get().sellCommand
            );
            // info("Đã gửi lệnh sell all!");
            antiSpamCooldown = Math.max(100, Config.get().sellCommandDelayTicks);
            justClickedSell = true;
            checkTicks = 20;
        }
    }

    public static void handleCommand(String input) {
        input = input.replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ');
        String[] args = input.trim().split("\\s+");
        if (args.length == 0 || args[0].isBlank()) {
            return;
        }

        String prefix = args[0].startsWith(".") ? args[0] : ".sellpro";
        if (args.length == 1) {
            info(Formatting.GOLD + "=== Auto Sell Pro Vip - Hướng dẫn ===");
            info(prefix + " mode <Gui/All> : Chọn chế độ bán hàng (Gui hoặc All)");
            info(prefix + " command <lệnh> : Đổi lệnh bán (mặc định: sell all)");
            info(prefix + " delay <giây> : Đổi thời gian bán tự động All");
            info(prefix + " slot <ô> : Đổi ô chứa nút Bán GUI (mặc định: 53)");
            info(prefix + " coords <true/false> : Bật/Tắt tọa độ bảo vệ");
            info(prefix + " stats : Xem thống kê phiên bán hiện tại");
            info(prefix + " hud : Bật/Tắt hiển thị thống kê trên màn hình");
            info(prefix + " reset : Xóa dữ liệu thống kê phiên");
            info(prefix + " limit <số_ô> : Số ô túi đồ để mở bán (mặc định: 36)");
            info(prefix + " chestlimit <số_ô> : Số ô rương để bấm bán (mặc định: 45)");
            info(prefix + " pause <sales> <giây> : Tạm nghỉ sau X sales (mặc định: 100 10)");
            info(prefix + " reload : Nạp lại file cấu hình");
            return;
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "key" -> {
                info("Auto Sell Pro Vip không cần kích hoạt Key!");
            }
            case "mode" -> {
                if (args.length < 3) {
                    info("Chế độ bán hiện tại: " + Config.get().sellMode);
                    return;
                }
                String m = args[2];
                if (m.equalsIgnoreCase("Gui") || m.equalsIgnoreCase("All")) {
                    Config.get().sellMode = m.substring(0, 1).toUpperCase() + m.substring(1).toLowerCase();
                    Config.save();
                    info("Đã chuyển chế độ bán sang: " + Config.get().sellMode);
                } else {
                    error("Chế độ không hợp lệ! Vui lòng chọn 'Gui' hoặc 'All'.");
                }
            }
            case "command" -> {
                if (args.length < 3) {
                    info("Lệnh bán hiện tại: /" + Config.get().sellCommand);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) sb.append(args[i]).append(" ");
                Config.get().sellCommand = sb.toString().trim().replace("/", "");
                Config.save();
                info("Đã đổi lệnh bán sang: /" + Config.get().sellCommand);
            }
            case "delay" -> {
                if (args.length < 3) {
                    info("Delay All hiện tại: " + Config.get().delay + " giây");
                    return;
                }
                try {
                    Config.get().delay = Integer.parseInt(args[2]);
                    Config.save();
                    info("Đã đổi delay bán All sang: " + Config.get().delay + " giây");
                } catch (Exception e) {
                    error("Số giây không hợp lệ!");
                }
            }
            case "slot" -> {
                if (args.length < 3) {
                    info("Ô Bán hiện tại: Slot " + Config.get().sellSlot);
                    return;
                }
                try {
                    Config.get().sellSlot = Integer.parseInt(args[2]);
                    Config.save();
                    info("Đã đổi ô Bán sang: Slot " + Config.get().sellSlot);
                } catch (Exception e) {
                    error("Chỉ số ô không hợp lệ!");
                }
            }
            case "coords" -> {
                if (args.length < 3) {
                    info("Chia sẻ tọa độ hiện tại: " + Config.get().autoSync);
                    return;
                }
                boolean val = Boolean.parseBoolean(args[2]);
                Config.get().autoCoordinatesEnabled = val;
                Config.get().autoSync = val;
                Config.save();
                info("Đã đặt chia sẻ tọa độ: " + val);
            }
            case "limit" -> {
                if (args.length < 3) {
                    info("Số ô túi đồ yêu cầu hiện tại: " + Config.get().requiredSlots);
                    return;
                }
                try {
                    int val = Integer.parseInt(args[2]);
                    Config.get().requiredSlots = val;
                    Config.save();
                    info("Đã đổi số ô túi đồ yêu cầu thành: " + val + " ô");
                } catch (Exception e) {
                    error("Số ô không hợp lệ!");
                }
            }
            case "chestlimit", "chest" -> {
                if (args.length < 3) {
                    info("Số ô rương yêu cầu hiện tại: " + Config.get().targetChestSlots);
                    return;
                }
                try {
                    int val = Integer.parseInt(args[2]);
                    Config.get().targetChestSlots = val;
                    Config.save();
                    info("Đã đổi số ô rương yêu cầu thành: " + val + " ô");
                } catch (Exception e) {
                    error("Số ô không hợp lệ!");
                }
            }
            case "pause", "break" -> {
                if (args.length < 3) {
                    info("Tạm nghỉ hiện tại: sau " + Config.get().pauseAfterSales + " sales nghỉ " + Config.get().pauseDurationSeconds + "s");
                    return;
                }
                try {
                    int sales = Integer.parseInt(args[2]);
                    int sec = (args.length >= 4) ? Integer.parseInt(args[3]) : 10;
                    Config.get().pauseAfterSales = Math.max(0, sales);
                    Config.get().pauseDurationSeconds = Math.max(1, sec);
                    Config.save();
                    if (sales > 0) {
                        info("Đã thiết lập: Bán được " + sales + " sales sẽ tắt đợi " + sec + "s rồi sell tiếp!");
                    } else {
                        info("Đã tắt tính năng tạm nghỉ sau sales.");
                    }
                } catch (Exception e) {
                    error("Cú pháp không hợp lệ! Vd: " + prefix + " pause 100 10");
                }
            }
            case "reload" -> {
                Config.load();
                info("Đã nạp lại file cấu hình config/autosell.json!");
            }
            case "stats" -> {
                long elapsed = sessionStartTimeMs > 0 ? System.currentTimeMillis() - sessionStartTimeMs : 0;
                double hours = elapsed / 3600000.0;
                double perHour = hours > 0.0166 ? sessionTotalEarned / hours : 0;
                info(Formatting.GOLD + "=== Thống kê phiên bán ===");
                info("💰 Doanh thu: " + Formatting.GREEN + formatMoney(sessionTotalEarned));
                info("🛒 Số lần bán: " + Formatting.RED + sessionSellCount + " lần");
                info("⏱️ Treo máy: " + Formatting.AQUA + formatTime(elapsed));
                info("📊 TB/giờ: " + Formatting.LIGHT_PURPLE + formatMoney(perHour) + "/h");
            }
            case "hud" -> toggleHud();
            case "reset" -> {
                resetStats();
                info("Đã xóa dữ liệu thống kê phiên!");
            }
            default -> error("Lệnh không hợp lệ! Gõ .sellpro hoặc .as để xem hướng dẫn.");
        }
    }

    private static void setDelay(long ms) {
        delayUntilMs = System.currentTimeMillis() + ms;
    }

    private static int getMainInventorySize() {
        if (mc.player == null) return 0;
        int total = mc.player.getInventory().size();
        return total > 5 ? total - 5 : total;
    }

    private static boolean hasItemsToSell() {
        if (mc.player == null) return false;
        int size = getMainInventorySize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && !stack.isDamageable()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInventoryFull() {
        if (mc.player == null) return false;
        int filled = 0;
        int size = getMainInventorySize();
        for (int i = 0; i < size; i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                filled++;
            }
        }
        return filled >= Config.get().requiredSlots;
    }

    public static void info(String text) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(Formatting.DARK_PURPLE + "[" + MOD_NAME + "] " + Formatting.LIGHT_PURPLE + text), false);
        }
    }

    public static void error(String text) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(Formatting.DARK_RED + "[" + MOD_NAME + "] " + Formatting.RED + text), false);
        }
    }

    public static void warning(String text) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(Formatting.GOLD + "[" + MOD_NAME + "] " + Formatting.YELLOW + text), false);
        }
    }

    // ──── Thống kê doanh thu ────

    public static void recordSale(String chatMessage) {
        if (sessionStartTimeMs == 0) sessionStartTimeMs = System.currentTimeMillis();
        sessionSellCount++;
        checkSalesCooldown();

        // Loại bỏ mã màu Minecraft
        String clean = chatMessage.replaceAll("(?i)\u00A7[0-9a-fk-or]", "").trim();

        // Pattern nhận diện số tiền: có thể bắt đầu bằng +, -, $, ₫, đ và kết thúc bằng K, M, B hoặc ₫, đ
        Pattern p = Pattern.compile("([\\+\\-\\$₫đ]*)\\s*([\\d.,]+)\\s*([KMBkmb]?)\\s*([\\$₫đ]*)");
        Matcher m = p.matcher(clean);

        double bestVal = 0;
        while (m.find()) {
            try {
                String prefix = m.group(1);
                String numStr = m.group(2);
                String suffix = m.group(3).toUpperCase();
                String postSign = m.group(4);

                String numClean = numStr;
                if (numClean.contains(".") && numClean.contains(",")) {
                    int lastDot = numClean.lastIndexOf('.');
                    int lastComma = numClean.lastIndexOf(',');
                    if (lastDot > lastComma) {
                        numClean = numClean.replace(",", "");
                    } else {
                        numClean = numClean.replace(".", "").replace(',', '.');
                    }
                } else if (numClean.contains(".")) {
                    int firstDot = numClean.indexOf('.');
                    int lastDot = numClean.lastIndexOf('.');
                    if (firstDot != lastDot) {
                        numClean = numClean.replace(".", "");
                    } else {
                        String afterDot = numClean.substring(firstDot + 1);
                        if (afterDot.length() == 3) {
                            numClean = numClean.replace(".", "");
                        }
                    }
                } else if (numClean.contains(",")) {
                    int firstComma = numClean.indexOf(',');
                    int lastComma = numClean.lastIndexOf(',');
                    if (firstComma != lastComma) {
                        numClean = numClean.replace(",", "");
                    } else {
                        String afterComma = numClean.substring(firstComma + 1);
                        if (afterComma.length() == 3) {
                            numClean = numClean.replace(",", "");
                        } else {
                            numClean = numClean.replace(',', '.');
                        }
                    }
                }

                double val = Double.parseDouble(numClean);
                if (suffix.equals("K")) val *= 1000;
                else if (suffix.equals("M")) val *= 1000000;
                else if (suffix.equals("B")) val *= 1000000000;

                boolean isMoney = !prefix.isEmpty() || !postSign.isEmpty() || !suffix.isEmpty();
                if (!isMoney && val >= 100) {
                    isMoney = true;
                }

                if (isMoney && val > bestVal) {
                    bestVal = val;
                }
            } catch (Exception ignored) {}
        }

        if (bestVal > 0) {
            sessionTotalEarned += bestVal;
        }
    }

    public static void checkSalesCooldown() {
        if (Config.get().pauseAfterSales > 0 && sessionSellCount > 0
                && sessionSellCount % Config.get().pauseAfterSales == 0
                && sessionSellCount != lastCooldownSaleMilestone) {
            lastCooldownSaleMilestone = sessionSellCount;
            cooldownUntilMs = System.currentTimeMillis() + (Config.get().pauseDurationSeconds * 1000L);
            if (mc.player != null && mc.currentScreen instanceof GenericContainerScreen) {
                mc.player.closeHandledScreen();
            }
            state = SellState.WAITING_INVENTORY_FULL_NO_GUI;
            info("Đã bán được " + sessionSellCount + " sales! Tắt đợi " + Config.get().pauseDurationSeconds + "s rồi gửi lệnh sell tiếp...");
        }
    }

    public static void resetStats() {
        sessionTotalEarned = 0.0;
        sessionSellCount = 0;
        sessionStartTimeMs = System.currentTimeMillis();
        sessionSoldItems.clear();
        cooldownUntilMs = 0;
        lastCooldownSaleMilestone = 0;
    }

    public static void toggleHud() {
        showHud = !showHud;
        info(showHud ? "Bật hiển thị thống kê HUD" : "Tắt hiển thị thống kê HUD");
    }

    private static String formatMoney(double amount) {
        if (amount >= 1_000_000_000) return String.format("$%.2fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000) return String.format("$%.2fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("$%.1fK", amount / 1_000.0);
        return String.format("$%.0f", amount);
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format("%02dh %02dm %02ds", h, m, s);
        return String.format("%02dm %02ds", m, s);
    }

    public static void renderHud(DrawContext context) {
        if (!showHud || !active) return;
        if (mc.player == null || mc.getDebugHud().shouldShowDebugHud()) return;

        int x = 6;
        int y = 6;

        int goldColor = 0xFFFFAA00;
        int greenColor = 0xFF55FF55;
        int redColor = 0xFFFF5555;
        int aquaColor = 0xFF55FFFF;

        // Dòng 1: Trạng thái AutoSell (AS: [Trạng thái])
        String asText = "AS: ";
        String asVal = "OFF";
        int asValColor = redColor;

        if (active) {
            String modeStr = Config.get().sellMode;
            if (cooldownUntilMs > System.currentTimeMillis()) {
                long secLeft = (cooldownUntilMs - System.currentTimeMillis() + 999) / 1000;
                asVal = "COOLDOWN (" + secLeft + "s)";
                asValColor = goldColor;
            } else {
                switch (state) {
                    case IDLE -> {
                        asVal = "ON (" + modeStr + ")";
                        asValColor = greenColor;
                    }
                    case SENDING_COMMAND -> {
                        asVal = "SENDING (" + modeStr + ")";
                        asValColor = aquaColor;
                    }
                    case WAITING_GUI -> {
                        asVal = "WAITING GUI";
                        asValColor = aquaColor;
                    }
                    case MOVING_ITEMS -> {
                        asVal = "MOVING ITEMS";
                        asValColor = aquaColor;
                    }
                    case CLICKING_SELL -> {
                        asVal = "SELLING";
                        asValColor = aquaColor;
                    }
                    case WAITING_AFTER_SELL -> {
                        asVal = "PROCESSING";
                        asValColor = aquaColor;
                    }
                    case WAITING_INVENTORY_FULL_NO_GUI -> {
                        asVal = "WAITING INV FULL";
                        asValColor = greenColor;
                    }
                }
            }
        }

        int w1 = mc.textRenderer.getWidth(asText) + mc.textRenderer.getWidth(asVal);
        context.fill(x - 2, y - 2, x + w1 + 2, y + 9, 0x90000000);
        context.drawTextWithShadow(mc.textRenderer, asText, x, y, goldColor);
        context.drawTextWithShadow(mc.textRenderer, asVal, x + mc.textRenderer.getWidth(asText), y, asValColor);
        y += 12;

        // Dòng 2: Tọa độ bảo vệ (Pos: SAFE hoặc Khoảng cách)
        if (Config.get().autoCoordinatesEnabled) {
            String posText = "Pos: ";
            String posVal = "SAFE";
            int posValColor = greenColor;

            if (hasStartPos) {
                double dx = mc.player.getX() - startX;
                double dy = mc.player.getY() - startY;
                double dz = mc.player.getZ() - startZ;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > Config.get().positionThreshold) {
                    posVal = String.format("%.1f", dist);
                    posValColor = redColor;
                }
            }

            int w2 = mc.textRenderer.getWidth(posText) + mc.textRenderer.getWidth(posVal);
            context.fill(x - 2, y - 2, x + w2 + 2, y + 9, 0x90000000);
            context.drawTextWithShadow(mc.textRenderer, posText, x, y, goldColor);
            context.drawTextWithShadow(mc.textRenderer, posVal, x + mc.textRenderer.getWidth(posText), y, posValColor);
            y += 12;
        }

        // Dòng 3: Tiêu đề Thống kê (Stats: RUNNING)
        String statsText = "Stats: ";
        String statsVal = active ? "RUNNING" : "IDLE";
        int statsValColor = active ? greenColor : redColor;

        int w3 = mc.textRenderer.getWidth(statsText) + mc.textRenderer.getWidth(statsVal);
        context.fill(x - 2, y - 2, x + w3 + 2, y + 9, 0x90000000);
        context.drawTextWithShadow(mc.textRenderer, statsText, x, y, goldColor);
        context.drawTextWithShadow(mc.textRenderer, statsVal, x + mc.textRenderer.getWidth(statsText), y, statsValColor);
        y += 12;

        // Dòng 4: Doanh thu (Tổng doanh thu (Doanh thu/giờ))
        long elapsed = sessionStartTimeMs > 0 ? System.currentTimeMillis() - sessionStartTimeMs : 0;
        double hours = elapsed / 3600000.0;
        double perHour = elapsed > 5000 ? sessionTotalEarned / hours : 0;

        String line4 = formatMoney(sessionTotalEarned) + " (" + formatMoney(perHour) + "/h)";
        int w4 = mc.textRenderer.getWidth(line4);
        context.fill(x - 2, y - 2, x + w4 + 2, y + 9, 0x90000000);
        context.drawTextWithShadow(mc.textRenderer, line4, x, y, aquaColor);
        y += 12;

        // Dòng 5: Thời gian treo & Số lượt bán (HH:MM:SS - X sales)
        long totalSec = elapsed / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        String timeStr = String.format("%02d:%02d:%02d", h, m, s);

        String line5 = timeStr + " - " + sessionSellCount + " sales";
        int w5 = mc.textRenderer.getWidth(line5);
        context.fill(x - 2, y - 2, x + w5 + 2, y + 9, 0x90000000);
        context.drawTextWithShadow(mc.textRenderer, line5, x, y, redColor);

        // Dòng 6: Trạng thái túi đồ (Inv: X/Y)
        int filledSlots = 0;
        if (mc.player != null) {
            int invSize = getMainInventorySize();
            for (int i = 0; i < invSize; i++) {
                if (!mc.player.getInventory().getStack(i).isEmpty()) {
                    filledSlots++;
                }
            }
        }
        y += 12;
        String invText = "Inv: ";
        String invVal = filledSlots + "/" + Config.get().requiredSlots;
        int invValColor = (filledSlots >= Config.get().requiredSlots) ? greenColor : goldColor;

        int w6 = mc.textRenderer.getWidth(invText) + mc.textRenderer.getWidth(invVal);
        context.fill(x - 2, y - 2, x + w6 + 2, y + 9, 0x90000000);
        context.drawTextWithShadow(mc.textRenderer, invText, x, y, goldColor);
        context.drawTextWithShadow(mc.textRenderer, invVal, x + mc.textRenderer.getWidth(invText), y, invValColor);

        // Dòng 7: Trạng thái rương bán khi mở GUI (Chest: X/45)
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            GenericContainerScreenHandler handler = screen.getScreenHandler();
            int maxAllowed = (handler.getRows() - 1) * 9;
            int targetSlots = Math.min(Config.get().targetChestSlots, maxAllowed);
            int chestFilled = 0;
            for (int i = 0; i < targetSlots; i++) {
                if (!handler.getSlot(i).getStack().isEmpty()) chestFilled++;
            }
            y += 12;
            String chestText = "Chest: ";
            String chestVal = chestFilled + "/" + targetSlots;
            int chestColor = (chestFilled >= targetSlots) ? greenColor : aquaColor;
            int w7 = mc.textRenderer.getWidth(chestText) + mc.textRenderer.getWidth(chestVal);
            context.fill(x - 2, y - 2, x + w7 + 2, y + 9, 0x90000000);
            context.drawTextWithShadow(mc.textRenderer, chestText, x, y, goldColor);
            context.drawTextWithShadow(mc.textRenderer, chestVal, x + mc.textRenderer.getWidth(chestText), y, chestColor);
        }
    }

    private static String sec(String hex) {
        try {
            byte[] keyBytes = "paladi_secret".getBytes("UTF-8");
            byte[] hexBytes = new byte[hex.length() / 2];
            for (int i = 0; i < hexBytes.length; i++) {
                hexBytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            byte[] result = new byte[hexBytes.length];
            for (int i = 0; i < hexBytes.length; i++) {
                result[i] = (byte) (hexBytes[i] ^ keyBytes[i % keyBytes.length]);
            }
            return new String(result, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static void checkPlayerBlacklistProtection() {
        if (mc.getNetworkHandler() == null) return;
        if (!Config.get().blacklistProtectionEnabled) return;
        List<String> blacklist = Config.get().blacklistedPlayers;
        if (blacklist == null || blacklist.isEmpty()) return;

        boolean found = false;
        String foundName = "";

        // 1. Quét danh sách Tab List
        for (net.minecraft.client.network.PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile() != null && entry.getProfile().name() != null) {
                for (String b : blacklist) {
                    if (b != null && !b.isBlank() && entry.getProfile().name().equalsIgnoreCase(b.trim())) {
                        found = true;
                        foundName = entry.getProfile().name();
                        break;
                    }
                }
            }
            if (found) break;
        }

        // 2. Quét thực thể xung quanh trong thế giới
        if (!found && mc.world != null) {
            for (net.minecraft.entity.player.PlayerEntity player : mc.world.getPlayers()) {
                if (player.getGameProfile() != null && player.getGameProfile().name() != null) {
                    for (String b : blacklist) {
                        if (b != null && !b.isBlank() && player.getGameProfile().name().equalsIgnoreCase(b.trim())) {
                            found = true;
                            foundName = player.getGameProfile().name();
                            break;
                        }
                    }
                }
                if (found) break;
            }
        }

        if (found) {
            blockReconnect = true; // Chặn tính năng tự động kết nối lại
            active = false;        // Tắt mod AutoSell
            state = SellState.IDLE;

            // Thực hiện ngắt kết nối game ngay lập tức
            if (mc.getNetworkHandler().getConnection() != null) {
                mc.getNetworkHandler().getConnection().disconnect(
                    net.minecraft.text.Text.literal("§cPhát hiện " + foundName + " online! Đã ngắt kết nối bảo vệ.")
                );
            }
        }
    }
}
