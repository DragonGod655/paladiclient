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

    public SpawnerProtect() {
        super(Categories.DONUT, "spawner-protect", "Tự động đào spawner khi có người hoặc tiếng bước chân lại gần.");
    }

    @Override
    public void onActivate() {
        mineTimer = 0;
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (!detectSound.get()) return;
        
        String soundName = event.sound.getId().getPath().toLowerCase();
        if (soundName.contains("step") || soundName.contains("footsteps")) {
            double dist = Math.sqrt(mc.player.squaredDistanceTo(event.sound.getX(), event.sound.getY(), event.sound.getZ()));
            if (dist <= range.get()) {
                triggerMine("tiếng bước chân");
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mineTimer > 0) {
            mineTimer--;
            mineSpawners();
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
    }

    private void triggerMine(String cause) {
        if (mineTimer == 0 && alertChat.get()) {
            info("Phát hiện " + cause + "! Bắt đầu đào spawner...");
        }
        // Đào liên tục trong 20 tick (1 giây) sau mỗi lần phát hiện
        mineTimer = 20;
    }

    private void mineSpawners() {
        int radius = mineRadius.get();
        BlockIterator.register(radius, radius, (pos, state) -> {
            if (state.getBlock() == Blocks.SPAWNER) {
                // Tìm cúp có Silk Touch trong toàn bộ kho đồ
                FindItemResult pickaxe = InvUtils.find(itemStack -> itemStack.isIn(ItemTags.PICKAXES) && Utils.hasEnchantment(itemStack, Enchantments.SILK_TOUCH));
                
                if (pickaxe.found()) {
                    // Nếu nằm trong túi đồ nhưng không ở hotbar, chuyển nó vào hotbar (ô số 0)
                    if (!pickaxe.isHotbar()) {
                        InvUtils.move().from(pickaxe.slot()).toHotbar(0);
                    }
                    InvUtils.swap(pickaxe.slot(), true);
                } else {
                    if (mineTimer == 19 && alertChat.get()) warning("Không tìm thấy cúp có Mềm Mại (Silk Touch) trong kho đồ!");
                    return; // Dừng lại, không đào nếu không có cúp mềm mại để bảo vệ spawner
                }
                
                // Tiến hành đào (chuyển tay & gửi packet break)
                BlockUtils.breakBlock(pos, true);
                InvUtils.swapBack();
            }
        });
    }
}
