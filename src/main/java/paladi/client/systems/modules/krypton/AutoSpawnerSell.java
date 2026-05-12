package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class AutoSpawnerSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> sellCommand = sgGeneral.add(new StringSetting.Builder()
        .name("sell-command").defaultValue("/sellspawner").build()
    );
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").defaultValue(20).min(5).sliderMax(120).build()
    );

    private int tickCounter = 0;

    public AutoSpawnerSell() {
        super(Categories.DONUT, "auto-spawner-sell", "Automatically sells spawners using server commands.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        tickCounter++;
        if (tickCounter < delay.get() * 20) return;
        tickCounter = 0;
        mc.player.networkHandler.sendChatCommand(sellCommand.get().replace("/", ""));
        info("Sold spawner!");
    }
}
