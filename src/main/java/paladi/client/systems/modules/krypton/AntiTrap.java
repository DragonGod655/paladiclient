package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;

public class AntiTrap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("detection-range")
        .description("Range to detect traps.")
        .defaultValue(5.0)
        .min(1).sliderMax(10)
        .build()
    );

    private final Setting<Boolean> alertChat = sgGeneral.add(new BoolSetting.Builder()
        .name("alert")
        .description("Alert in chat when trapped.")
        .defaultValue(true)
        .build()
    );

    public AntiTrap() {
        super(Categories.DONUT, "anti-trap", "Detects and alerts when you are being trapped.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int solidCount = 0;

        // Check blocks around player for enclosure
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    BlockPos check = playerPos.add(x, y, z);
                    if (x == 0 && z == 0) continue;
                    if (!mc.world.getBlockState(check).isAir()) solidCount++;
                }
            }
        }

        // If surrounded by 14+ solid blocks, likely trapped
        if (solidCount >= 14 && alertChat.get()) {
            warning("You might be trapped! Solid blocks around: %d", solidCount);
        }
    }
}
