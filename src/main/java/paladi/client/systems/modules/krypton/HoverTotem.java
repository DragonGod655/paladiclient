package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static paladi.client.PaladiClient.mc;

public class HoverTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> healthThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("health")
        .description("Health threshold to equip totem in offhand.")
        .defaultValue(14.0)
        .min(1)
        .sliderMax(20)
        .build()
    );

    public HoverTotem() {
        super(Categories.COMBAT, "hover-totem", "Keeps totem ready in offhand when health is low.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        double currentHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        double predictedDmg = PlayerUtils.possibleHealthReductions(true, true);
        if ((currentHp - predictedDmg) <= healthThreshold.get()) {
            // Fast swap from hotbar
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
