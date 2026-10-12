/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import paladi.client.events.render.Render3DEvent;
import paladi.client.events.world.PlaySoundEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.renderer.ShapeMode;
import paladi.client.settings.*;
import paladi.client.systems.friends.Friends;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.ChatUtils;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.render.color.Color;
import paladi.client.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SpawnerProtect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTrigger = settings.createGroup("Điều Kiện Kích Hoạt");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> mineRadius = sgGeneral.add(new IntSetting.Builder()
        .name("mine-radius")
        .description("Bán kính đào spawner xung quanh bạn (4 = vùng 9x9x9).")
        .defaultValue(4)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> packetsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("packets-per-tick")
        .description("Số lần gửi packet đào mỗi tick (Đào sạch các khối gộp 128x/256x siêu tốc).")
        .defaultValue(3)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> detectSound = sgTrigger.add(new BoolSetting.Builder()
        .name("detect-footsteps")
        .description("Phát hiện qua tiếng bước chân (hữu ích khi địch tàng hình).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectPlayers = sgTrigger.add(new BoolSetting.Builder()
        .name("detect-players")
        .description("Phát hiện người chơi lại gần (không phải bạn bè).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> detectionRange = sgTrigger.add(new DoubleSetting.Builder()
        .name("detection-range")
        .description("Khoảng cách phát hiện người chơi hoặc tiếng bước chân.")
        .defaultValue(24.0)
        .min(1.0)
        .sliderMax(64.0)
        .build()
    );

    private final Setting<Boolean> checkFriends = sgTrigger.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Bỏ qua bạn bè trong danh sách Friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> alertChat = sgTrigger.add(new BoolSetting.Builder()
        .name("alert")
        .description("Thông báo trên chat khi phát hiện và bắt đầu đào.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> boxColor = sgRender.add(new ColorSetting.Builder()
        .name("box-color")
        .description("Màu highlight các khối Spawner còn tồn tại.")
        .defaultValue(new SettingColor(255, 0, 0, 120))
        .build()
    );

    private final List<BlockPos> spawnerList = new ArrayList<>();
    private BlockPos currentTarget = null;
    private int verifyAirTicks = 0;
    private int triggerCooldown = 0;
    private boolean isSneakingActive = false;

    public SpawnerProtect() {
        super(Categories.DONUT, "spawner-protect", "Tự động đào sạch Spawner gộp (128x) bằng cúp Silk Touch khi có người lạ hoặc tiếng bước chân.");
    }

    @Override
    public void onActivate() {
        spawnerList.clear();
        currentTarget = null;
        verifyAirTicks = 0;
        triggerCooldown = 0;
        isSneakingActive = false;
    }

    @Override
    public void onDeactivate() {
        spawnerList.clear();
        currentTarget = null;
        verifyAirTicks = 0;
        triggerCooldown = 0;
        if (isSneakingActive) {
            if (mc.options != null && mc.options.sneakKey != null) {
                mc.options.sneakKey.setPressed(false);
            }
            isSneakingActive = false;
        }
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (!detectSound.get() || mc.player == null || mc.world == null) return;

        String soundName = event.sound.getId().getPath().toLowerCase();
        if (soundName.contains("step") || soundName.contains("footsteps")) {
            double soundX = event.sound.getX();
            double soundY = event.sound.getY();
            double soundZ = event.sound.getZ();

            // Kiểm tra xem âm thanh này có phát ra từ bản thân hoặc bạn bè không
            boolean fromTrusted = false;
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player || (checkFriends.get() && Friends.get().isFriend(player))) {
                    double distSq = player.squaredDistanceTo(soundX, soundY, soundZ);
                    if (distSq < 2.0 * 2.0) {
                        fromTrusted = true;
                        break;
                    }
                }
            }

            if (fromTrusted) return;

            double dist = Math.sqrt(mc.player.squaredDistanceTo(soundX, soundY, soundZ));
            if (dist <= detectionRange.get()) {
                triggerPanicMine("tiếng bước chân lạ gần (" + String.format("%.1f", dist) + "m)");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        // 1. Quét người chơi (Nametag / Player Entity)
        if (detectPlayers.get()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player || !player.isAlive()) continue;
                if (checkFriends.get() && Friends.get().isFriend(player)) continue;

                if (mc.player.distanceTo(player) <= detectionRange.get()) {
                    triggerPanicMine("người chơi " + player.getName().getString());
                    break;
                }
            }
        }

        if (triggerCooldown > 0) {
            triggerCooldown--;
        }

        // Nếu không có trigger nào trong thời gian gần và không có mục tiêu dở -> Tắt sneak và nghỉ
        if (triggerCooldown == 0 && currentTarget == null) {
            if (isSneakingActive) {
                mc.options.sneakKey.setPressed(false);
                isSneakingActive = false;
            }
            return;
        }

        // 2. TÌM VÀ CHỌN CÚP SILK TOUCH
        int silkSlot = findSilkTouchPickaxeSlot();
        if (silkSlot == -1) {
            if (triggerCooldown == 29 && alertChat.get()) {
                ChatUtils.sendMsg(Formatting.RED, "[SpawnerProtect] CẢNH BÁO: Không có cúp Silk Touch trong túi đồ! Dừng đào để tránh mất Spawner!");
            }
            if (isSneakingActive) {
                mc.options.sneakKey.setPressed(false);
                isSneakingActive = false;
            }
            return;
        }

        // Luôn chuyển sang slot cúp Silk Touch (Tuyệt đối không cầm Spawner vừa rơi vào người)
        if (mc.player.getInventory().getSelectedSlot() != silkSlot) {
            InvUtils.swap(silkSlot, false);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(silkSlot));
        }

        // 3. Giữ Shift (Sneak) để server kích hoạt chế độ Shift-Mine (Lấy trọn 64 spawner/cú)
        if (!isSneakingActive) {
            mc.options.sneakKey.setPressed(true);
            isSneakingActive = true;
        }

        // 4. Kiểm tra mục tiêu hiện tại (Target Lock)
        if (currentTarget != null) {
            // Nếu khối tại vị trí này đã biến thành Air / không còn là Spawner
            if (!mc.world.getBlockState(currentTarget).isOf(Blocks.SPAWNER)) {
                verifyAirTicks++;
                if (verifyAirTicks >= 2) { // Đợi server xác nhận sạch hẳn sau 2 tick
                    currentTarget = null;
                    verifyAirTicks = 0;
                }
            }
        }

        // 5. Nếu chưa có mục tiêu hoặc khối cũ đã sạch -> Quét tìm khối Spawner gần nhất
        if (currentTarget == null) {
            scanSpawners();
            if (spawnerList.isEmpty()) {
                if (isSneakingActive) {
                    mc.options.sneakKey.setPressed(false);
                    isSneakingActive = false;
                }
                return;
            }
            currentTarget = spawnerList.get(0);
        }

        // 6. Gửi chuỗi packet đào liên tục vào khối hiện tại (Xử lý khối 128x / 256x gộp)
        for (int i = 0; i < packetsPerTick.get(); i++) {
            if (!mc.world.getBlockState(currentTarget).isOf(Blocks.SPAWNER)) break;

            // Bắt đầu đào (Shift Mine)
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, currentTarget, Direction.UP
            ));

            // Kết thúc đào (Thu hồi 64 spawner)
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentTarget, Direction.UP
            ));

            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
    }

    private void triggerPanicMine(String cause) {
        if (triggerCooldown == 0 && alertChat.get()) {
            ChatUtils.sendMsg(Formatting.RED, "[SpawnerProtect] PHÁT HIỆN: " + cause + " -> ĐANG ĐÀO CẤP TỐC TẤT CẢ SPAWNER!");
        }
        triggerCooldown = 30; // Duy trì trạng thái đào khẩn cấp trong 30 tick (1.5s) và reset liên tục nếu địch vẫn ở gần
    }

    private void scanSpawners() {
        spawnerList.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = mineRadius.get();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).isOf(Blocks.SPAWNER)) {
                        spawnerList.add(pos);
                    }
                }
            }
        }

        // Sắp xếp ưu tiên spawner gần người chơi nhất
        spawnerList.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)));
    }

    private int findSilkTouchPickaxeSlot() {
        // Kiểm tra Hotbar (0 -> 8)
        for (int i = 0; i < 9; i++) {
            if (isSilkTouchPickaxe(mc.player.getInventory().getStack(i))) {
                return i;
            }
        }

        // Nếu trên hotbar không có -> Tìm trong Main Inventory (9 -> 35) và chuyển vào Hotbar
        FindItemResult invResult = InvUtils.find(this::isSilkTouchPickaxe, 9, 35);
        if (invResult.found()) {
            int currentHotbar = mc.player.getInventory().getSelectedSlot();
            InvUtils.move().from(invResult.slot()).toHotbar(currentHotbar);
            return currentHotbar;
        }

        return -1;
    }

    private boolean isSilkTouchPickaxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isIn(ItemTags.PICKAXES)) return false;

        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.getEnchantments()) {
                if (entry.getIdAsString().toLowerCase().contains("silk_touch")) {
                    return true;
                }
            }
        }

        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (spawnerList.isEmpty() && currentTarget == null) return;

        Color side = boxColor.get();
        Color line = new Color(side.r, side.g, side.b, 255);

        if (currentTarget != null && mc.world.getBlockState(currentTarget).isOf(Blocks.SPAWNER)) {
            event.renderer.box(currentTarget, side, line, ShapeMode.Both, 0);
        }

        for (BlockPos pos : spawnerList) {
            if (pos.equals(currentTarget)) continue;
            event.renderer.box(pos, side, line, ShapeMode.Both, 0);
        }
    }
}
