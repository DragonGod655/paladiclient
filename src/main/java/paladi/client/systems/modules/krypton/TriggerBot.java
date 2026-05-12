package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public class TriggerBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Attack range.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> playersOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("players-only")
        .description("Only attack players.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between attacks in ticks.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private int tickCounter = 0;

    public TriggerBot() {
        super(Categories.COMBAT, "trigger-bot", "Automatically attacks the entity you are looking at.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.getAttackCooldownProgress(0.5f) < 1) return;

        tickCounter++;
        if (tickCounter < delay.get()) return;

        Entity target = mc.crosshairTarget != null ? mc.targetedEntity : null;
        if (target == null) return;
        if (!(target instanceof LivingEntity living)) return;
        if (playersOnly.get() && !(target instanceof PlayerEntity)) return;
        if (mc.player.distanceTo(target) > range.get()) return;
        if (living.isDead() || living.getHealth() <= 0) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        tickCounter = 0;
    }
}
