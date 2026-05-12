package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class ChatMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> message = sgGeneral.add(new StringSetting.Builder()
        .name("message").description("Message to send.").defaultValue("Krypton+ on top!").build()
    );
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Delay in seconds.").defaultValue(30).min(1).sliderMax(120).build()
    );

    private int tickCounter = 0;

    public ChatMacro() {
        super(Categories.CLIENT, "chat-macro", "Sends a chat message on a timer.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        tickCounter++;
        if (tickCounter < delay.get() * 20) return;
        tickCounter = 0;
        mc.player.networkHandler.sendChatMessage(message.get());
    }
}
