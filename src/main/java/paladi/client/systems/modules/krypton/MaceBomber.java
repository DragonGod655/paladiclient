package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class MaceBomber extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").defaultValue(5.0).min(1).sliderMax(8).build());

    public MaceBomber() { super(Categories.COMBAT, "mace-bomber", "Auto-attacks with mace when falling from height for maximum damage."); }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.getVelocity().y >= -0.5) return;

        var result = InvUtils.findInHotbar(Items.MACE);
        if (!result.found()) return;

        Entity closest = null;
        double closestDist = range.get();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && player != mc.player) {
                double dist = mc.player.distanceTo(player);
                if (dist < closestDist) { closestDist = dist; closest = player; }
            }
        }

        if (closest != null) {
            InvUtils.swap(result.slot(), true);
            mc.interactionManager.attackEntity(mc.player, closest);
            mc.player.swingHand(Hand.MAIN_HAND);
            InvUtils.swapBack();
        }
    }
}
