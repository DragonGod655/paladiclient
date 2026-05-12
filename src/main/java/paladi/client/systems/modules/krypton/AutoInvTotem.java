package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;

public class AutoInvTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> healthThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("health-threshold").description("Swap totem when health below this.").defaultValue(10).min(1).sliderMax(20).build()
    );

    public AutoInvTotem() {
        super(Categories.COMBAT, "auto-inv-totem", "Automatically moves totems from inventory to offhand when low health.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (mc.player.getHealth() > healthThreshold.get()) return;
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 40, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                info("Totem moved to offhand!");
                break;
            }
        }
    }
}
