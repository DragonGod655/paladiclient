package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;

public class CordSnapper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> logToChat = sgGeneral.add(new BoolSetting.Builder()
        .name("log-to-chat")
        .description("Show coordinates in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("interval")
        .description("Interval in seconds between coordinate snapshots.")
        .defaultValue(10)
        .min(1).sliderMax(60)
        .build()
    );

    private int tickCounter = 0;

    public CordSnapper() {
        super(Categories.MISC, "cord-snapper", "Periodically logs your current coordinates.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        tickCounter++;
        if (tickCounter < interval.get() * 20) return;
        tickCounter = 0;

        BlockPos pos = mc.player.getBlockPos();
        String dimension = mc.world.getRegistryKey().getValue().getPath();
        String coords = String.format("[%s] X: %d  Y: %d  Z: %d", dimension, pos.getX(), pos.getY(), pos.getZ());

        if (logToChat.get()) info(coords);
    }
}
