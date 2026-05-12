package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;

import static paladi.client.PaladiClient.mc;

public class KryptonAutoTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> health = sgGeneral.add(new DoubleSetting.Builder()
        .name("health")
        .description("Health threshold to swap to totem.")
        .defaultValue(10.0)
        .min(1)
        .sliderMax(20)
        .build()
    );

    public KryptonAutoTotem() {
        super(Categories.COMBAT, "krypton-auto-totem", "Fastest totem switcher in the world.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            if (mc.player.getHealth() <= health.get()) {
                FindItemResult totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
                if (totem.found()) {
                    InvUtils.move().from(totem.slot()).toOffhand();
                }
            }
        }
    }
}
