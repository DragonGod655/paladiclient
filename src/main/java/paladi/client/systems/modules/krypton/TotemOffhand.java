package paladi.client.systems.modules.krypton;

import paladi.client.events.packets.PacketEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static paladi.client.PaladiClient.mc;

import paladi.client.utils.player.SlotUtils;
import net.minecraft.screen.slot.SlotActionType;

public class TotemOffhand extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public TotemOffhand() {
        super(Categories.COMBAT, "totem-offhand", "Keeps a totem in your offhand at all times without ghost items.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        equipTotem();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;
        if (p.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;

        Entity entity = p.getEntity(mc.world);
        if (entity == null || !entity.equals(mc.player)) return;

        equipTotem();
    }

    private void equipTotem() {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
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
