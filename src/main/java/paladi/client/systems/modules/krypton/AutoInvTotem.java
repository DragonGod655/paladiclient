package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.DoubleSetting;
import paladi.client.settings.Setting;
import paladi.client.settings.SettingGroup;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.player.SlotUtils;

public class AutoInvTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> healthThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("Swap totem when health below this.")
        .defaultValue(14.0)
        .min(1)
        .sliderMax(20)
        .build()
    );

    public AutoInvTotem() {
        super(Categories.COMBAT, "auto-inv-totem", "Automatically moves totems from inventory to offhand when low health without ghost items.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        if (mc.player.getHealth() > healthThreshold.get()) return;
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
        if (result.found() && !result.isOffhand()) {
            if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
                FindItemResult empty = InvUtils.findEmpty();
                if (empty.found()) {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, SlotUtils.indexToId(empty.slot()), 0, SlotActionType.PICKUP, mc.player);
                }
            }

            int slotId = SlotUtils.indexToId(result.slot());
            if (slotId != -1) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slotId, 40, SlotActionType.SWAP, mc.player);
            }
        }
    }
}
