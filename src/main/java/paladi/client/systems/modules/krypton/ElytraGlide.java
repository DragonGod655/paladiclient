package paladi.client.systems.modules.krypton;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
public class ElytraGlide extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> fallSpeed = sgGeneral.add(new DoubleSetting.Builder().name("fall-speed").defaultValue(-0.03).min(-0.1).sliderMax(0).build());
    private final Setting<Double> moveSpeed = sgGeneral.add(new DoubleSetting.Builder().name("move-speed").defaultValue(1.0).min(0.1).sliderMax(3).build());
    public ElytraGlide() { super(Categories.MISC, "elytra-glide", "Smooths out elytra flight for better gliding control."); }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || !mc.player.isGliding()) return;
        var vel = mc.player.getVelocity();
        if (vel.y < fallSpeed.get()) mc.player.setVelocity(vel.x * moveSpeed.get(), fallSpeed.get(), vel.z * moveSpeed.get());
    }
}
