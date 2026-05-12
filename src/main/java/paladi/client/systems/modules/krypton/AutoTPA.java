package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class AutoTPA extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> targetPlayer = sgGeneral.add(new StringSetting.Builder()
        .name("target")
        .description("Player to TPA to.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between TPA requests in seconds.")
        .defaultValue(30)
        .min(5).sliderMax(120)
        .build()
    );

    private final Setting<Boolean> tpaHere = sgGeneral.add(new BoolSetting.Builder()
        .name("tpa-here")
        .description("Use /tpahere instead of /tpa.")
        .defaultValue(false)
        .build()
    );

    private int tickCounter = 0;

    public AutoTPA() {
        super(Categories.MISC, "auto-tpa", "Automatically sends TPA requests to a target player.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || targetPlayer.get().isEmpty()) return;

        tickCounter++;
        if (tickCounter < delay.get() * 20) return;
        tickCounter = 0;

        String cmd = tpaHere.get() ? "tpahere" : "tpa";
        mc.player.networkHandler.sendChatCommand(cmd + " " + targetPlayer.get());
        info("Sent %s request to %s", cmd, targetPlayer.get());
    }
}
