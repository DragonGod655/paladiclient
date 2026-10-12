package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static paladi.client.PaladiClient.mc;

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

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (totemOffhand.get() && mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            // Try fast swap from hotbar
            FindItemResult hotbarTotem = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING);
            if (hotbarTotem.found() && hotbarTotem.isHotbar()) {
                swapHotbarToOffhand(hotbarTotem.slot());
                return;
            }

            // Move from inventory
            FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
            if (result.found() && !result.isOffhand()) {
                InvUtils.move().from(result.slot()).toOffhand();
                InvUtils.dropHand();
            }
        }
    }

    private void swapHotbarToOffhand(int slot) {
        if (slot < 0 || slot > 8) return;
        int prev = mc.player.getInventory().getSelectedSlot();
        if (prev != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            ((paladi.client.mixininterface.IClientPlayerInteractionManager) mc.interactionManager).Paladi$syncSelected();
        }
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));
        if (prev != slot) {
            mc.player.getInventory().setSelectedSlot(prev);
            ((paladi.client.mixininterface.IClientPlayerInteractionManager) mc.interactionManager).Paladi$syncSelected();
        }
    }
}
