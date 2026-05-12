package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;

import static paladi.client.PaladiClient.mc;

public class KryptonFlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Flight speed.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(5.0)
        .build()
    );

    public KryptonFlight() {
        super(Categories.MISC, "krypton-flight", "Professional high-speed flight.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().allowFlying = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        mc.player.getAbilities().allowFlying = true;
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(speed.get().floatValue() / 10f);
    }
}
