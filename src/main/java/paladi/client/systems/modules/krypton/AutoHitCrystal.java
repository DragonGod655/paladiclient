package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.Hand;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.Utils;

import static paladi.client.PaladiClient.mc;

public class AutoHitCrystal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The range at which to break crystals.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> chance = sgGeneral.add(new IntSetting.Builder()
        .name("chance")
        .description("The chance to attack each crystal.")
        .defaultValue(75)
        .min(0)
        .max(100)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> onlyOnObsidian = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-obsidian")
        .description("Only attack crystals that are on obsidian.")
        .defaultValue(true)
        .build()
    );

    public AutoHitCrystal() {
        super(Categories.COMBAT, "auto-hit-crystal", "Auto-breaks end crystals near you.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!mc.player.isAlive() || mc.currentScreen != null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (mc.player.distanceTo(crystal) > range.get()) continue;

            if (onlyOnObsidian.get()) {
                if (mc.world.getBlockState(crystal.getBlockPos().down()).getBlock() != Blocks.OBSIDIAN) continue;
            }

            if (Math.random() * 100 <= chance.get()) {
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}
