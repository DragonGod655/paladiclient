package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class AutoDoubleHand extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> totemOffhand = sgGeneral.add(new BoolSetting.Builder()
        .name("totem-offhand")
        .description("Keep totem in offhand.")
        .defaultValue(true)
        .build()
    );

    public AutoDoubleHand() {
        super(Categories.COMBAT, "auto-double-hand", "Manages main hand and offhand items for combat.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (totemOffhand.get()) {
            if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                // Find totem in inventory
                for (int i = 0; i < 36; i++) {
                    if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 40, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                        break;
                    }
                }
            }
        }
    }
}
