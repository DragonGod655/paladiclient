package paladi.client.systems.modules.krypton;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
public class AutoLoot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").defaultValue(4.5).min(1).sliderMax(6).build());
    public AutoLoot() { super(Categories.MISC, "auto-loot", "Automatically walks toward nearby dropped items to pick them up."); }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        ItemEntity closest = null;
        double closestDist = range.get();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity item) {
                double dist = mc.player.distanceTo(item);
                if (dist < closestDist) { closestDist = dist; closest = item; }
            }
        }
        if (closest != null) {
            double dx = closest.getX() - mc.player.getX();
            double dz = closest.getZ() - mc.player.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                mc.player.setVelocity(mc.player.getVelocity().add(dx / len * 0.2, 0, dz / len * 0.2));
            }
        }
    }
}
