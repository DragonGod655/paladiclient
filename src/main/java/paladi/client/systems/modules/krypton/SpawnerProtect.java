package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.tag.ItemTags;
import paladi.client.events.world.PlaySoundEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.friends.Friends;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.world.BlockIterator;
import paladi.client.utils.world.BlockUtils;
import paladi.client.utils.Utils;
import net.minecraft.util.math.BlockPos;

public class SpawnerProtect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("detection-range")
        .description("Khoảng cách phát hiện người chơi hoặc tiếng bước chân.")
        .defaultValue(15)
        .min(1)
        .sliderMax(50)
        .build()
    );

    private final Setting<Integer> mineRadius = sgGeneral.add(new IntSetting.Builder()
        .name("mine-radius")
        .description("Bán kính đào spawner xung quanh bạn.")
        .defaultValue(5)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> detectSound = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-footsteps")
        .description("Phát hiện qua tiếng bước chân (hữu ích khi địch tàng hình).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-players")
        .description("Phát hiện người chơi lại gần (không phải bạn bè).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> alertChat = sgGeneral.add(new BoolSetting.Builder()
        .name("alert")
        .description("Thông báo trên chat khi phát hiện và bắt đầu đào.")
        .defaultValue(true)
        .build()
    );

    private int mineTimer = 0;
    private BlockPos activeSpawnerPos = null;
    private boolean isSneakingPressed = false;
    private int breakDelayTicks = 0;
    private boolean isMiningActive = false;

    public SpawnerProtect() {
        super(Categories.DONUT, "spawner-protect", "Tự động đào spawner khi có người hoặc tiếng bước chân lại gần.");
    }

    @Override
    public void onActivate() {
        mineTimer = 0;
        activeSpawnerPos = null;
        isSneakingPressed = false;
        breakDelayTicks = 0;
        isMiningActive = false;
    }

    @Override
    public void onDeactivate() {
        if (isSneakingPressed) {
            if (mc.options != null && mc.options.sneakKey != null) {
                mc.options.sneakKey.setPressed(false);
            }
            isSneakingPressed = false;
        }
        activeSpawnerPos = null;
        breakDelayTicks = 0;
        isMiningActive = false;
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
                if (player == mc.player || Friends.get().isFriend(player)) {
                    double distSq = player.squaredDistanceTo(soundX, soundY, soundZ);
                    if (distSq < 2.0 * 2.0) { // Tiếng bước chân trong phạm vi 2 block của người thân
                        fromTrusted = true;
                        break;
                    }
                }
            }

            if (fromTrusted) return; // Bỏ qua tiếng chân của chính mình và bạn bè

            double dist = Math.sqrt(mc.player.squaredDistanceTo(soundX, soundY, soundZ));
            if (dist <= range.get()) {
                triggerMine("tiếng bước chân lạ");
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mineTimer > 0) {
            mineTimer--;
        }

        if (detectPlayers.get()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player || !player.isAlive()) continue;
                if (Friends.get().isFriend(player)) continue;
                
                if (mc.player.distanceTo(player) <= range.get()) {
                    triggerMine("người chơi " + player.getName().getString());
                    break;
                }
            }
        }

        // Bật trạng thái khai thác active khi được kích hoạt và phát hiện spawner trong tầm
        if (mineTimer > 0 && findClosestSpawner(mineRadius.get()) != null) {
            isMiningActive = true;
        }

        if (breakDelayTicks > 0) {
            breakDelayTicks--;
            if (isSneakingPressed) {
                mc.options.sneakKey.setPressed(false);
                isSneakingPressed = false;
            }
            return;
        }

        if (activeSpawnerPos != null) {
            if (mc.world != null && mc.world.getBlockState(activeSpawnerPos).getBlock() != Blocks.SPAWNER) {
                activeSpawnerPos = null;
                breakDelayTicks = 6;
                if (isSneakingPressed) {
                    mc.options.sneakKey.setPressed(false);
                    isSneakingPressed = false;
                }
                return;
            }
        }

        if (isMiningActive) {
            BlockPos closestSpawner = findClosestSpawner(mineRadius.get());
            if (closestSpawner != null) {
                activeSpawnerPos = closestSpawner;
            } else {
                // Đã đào sạch Spawner xung quanh
                activeSpawnerPos = null;
                isMiningActive = false;
            }
        }

        if (isMiningActive && activeSpawnerPos != null) {
            if (!isSneakingPressed) {
                mc.options.sneakKey.setPressed(true);
                isSneakingPressed = true;
            }
            mineSpawner(activeSpawnerPos);
        } else {
            if (isSneakingPressed) {
                mc.options.sneakKey.setPressed(false);
                isSneakingPressed = false;
            }
        }
    }

    private void triggerMine(String cause) {
        if (mineTimer == 0 && alertChat.get()) {
            info("Phát hiện " + cause + "! Bắt đầu đào spawner...");
        }
        mineTimer = 20;
    }

    private BlockPos findClosestSpawner(int radius) {
        if (mc.player == null || mc.world == null) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -radius; y <= radius; y++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (pos.getY() < mc.world.getBottomY() || pos.getY() > mc.world.getHeight()) continue;

                    if (mc.world.getBlockState(pos).getBlock() == Blocks.SPAWNER) {
                        double dist = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }

    private void mineSpawner(BlockPos pos) {
        FindItemResult pickaxe = InvUtils.find(itemStack -> itemStack.isIn(ItemTags.PICKAXES) && Utils.hasEnchantment(itemStack, Enchantments.SILK_TOUCH));
        
        if (pickaxe.found()) {
            if (!pickaxe.isHotbar()) {
                InvUtils.move().from(pickaxe.slot()).toHotbar(0);
            }
            InvUtils.swap(pickaxe.slot(), true);
        } else {
            if (mineTimer == 19 && alertChat.get()) warning("Không tìm thấy cúp có Mềm Mại (Silk Touch) trong kho đồ!");
            return;
        }
        
        BlockUtils.breakBlock(pos, true);
        InvUtils.swapBack();
    }
}
