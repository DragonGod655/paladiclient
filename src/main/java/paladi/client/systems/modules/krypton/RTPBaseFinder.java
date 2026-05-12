package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class RTPBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> rtpCommand = sgGeneral.add(new StringSetting.Builder()
        .name("rtp-command").defaultValue("/rtp").build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Delay between RTPs in seconds.").defaultValue(60).min(10).sliderMax(300).build()
    );

    private final Setting<Integer> minBlockEntities = sgGeneral.add(new IntSetting.Builder()
        .name("min-block-entities").description("Min block entities to flag as a base.").defaultValue(5).min(1).sliderMax(50).build()
    );

    private int tickCounter = 0;

    public RTPBaseFinder() {
        super(Categories.DONUT, "rtp-base-finder", "Uses /rtp to teleport randomly and scan for bases.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        tickCounter++;
        if (tickCounter < delay.get() * 20) return;
        tickCounter = 0;

        // Check loaded chunks for block entities
        var chunks = mc.world.getChunkManager();
        int baseScore = 0;
        var playerChunk = mc.world.getChunk(mc.player.getBlockPos());
        if (playerChunk != null) {
            baseScore = playerChunk.getBlockEntityPositions().size();
        }

        if (baseScore >= minBlockEntities.get()) {
            info("Possible base found! Block entities: %d at X:%d Z:%d",
                baseScore, mc.player.getBlockX(), mc.player.getBlockZ());
        } else {
            mc.player.networkHandler.sendChatCommand(rtpCommand.get().replace("/", ""));
            info("No base found, RTPing again...");
        }
    }
}
