package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.Hand;

public class ShieldBreaker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").defaultValue(4.5).min(1).sliderMax(6).build());

    public ShieldBreaker() { super(Categories.COMBAT, "shield-breaker", "Automatically switches to axe to break enemy shields."); }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        Entity target = mc.targetedEntity;
        if (target == null || !(target instanceof PlayerEntity player)) return;
        if (mc.player.distanceTo(target) > range.get()) return;

        if (player.isBlocking()) {
            var result = InvUtils.findInHotbar(stack -> stack.getItem() instanceof AxeItem);
            if (result.found()) {
                InvUtils.swap(result.slot(), true);
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.swapBack();
            }
        }
    }
}
