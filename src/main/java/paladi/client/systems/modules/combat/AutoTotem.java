/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.combat;

import paladi.client.events.packets.PacketEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import paladi.client.utils.player.SlotUtils;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Determines when to hold a totem, strict will always hold.")
        .defaultValue(Mode.Strict)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The ticks between slot movements.")
        .defaultValue(0)
        .min(0)
        .build()
    );

    private final Setting<Integer> health = sgGeneral.add(new IntSetting.Builder()
        .name("health")
        .description("The health to hold a totem at.")
        .defaultValue(14)
        .range(0, 36)
        .sliderMax(36)
        .visible(() -> mode.get() == Mode.Smart)
        .build()
    );

    private final Setting<Boolean> elytra = sgGeneral.add(new BoolSetting.Builder()
        .name("elytra")
        .description("Will always hold a totem when flying with elytra.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Smart)
        .build()
    );

    private final Setting<Boolean> fall = sgGeneral.add(new BoolSetting.Builder()
        .name("fall")
        .description("Will hold a totem when fall damage could kill you.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Smart)
        .build()
    );

    private final Setting<Boolean> explosion = sgGeneral.add(new BoolSetting.Builder()
        .name("explosion")
        .description("Will hold a totem when explosion damage could kill you.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Smart)
        .build()
    );

    public boolean locked;
    private int totems, ticks;

    public AutoTotem() {
        super(Categories.Combat, "auto-totem", "Tự động cầm Totem ở tay phụ (Offhand) bằng gói tin SWAP nguyên tử, chống 100% ghost totem.");
    }

    @Override
    public void onActivate() {
        ticks = delay.get();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;

        FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
        totems = result.count();

        if (totems <= 0) {
            locked = false;
            return;
        }

        if (ticks >= delay.get()) {
            boolean low = mc.player.getHealth() + mc.player.getAbsorptionAmount() - PlayerUtils.possibleHealthReductions(explosion.get(), fall.get()) <= health.get();
            boolean ely = elytra.get() && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA && mc.player.isGliding();

            locked = mode.get() == Mode.Strict || (mode.get() == Mode.Smart && (low || ely));

            if (locked && mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                equipTotemToOffhand(result);
            }

            ticks = 0;
            return;
        }

        ticks++;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;
        if (p.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;

        Entity entity = p.getEntity(mc.world);
        if (entity == null || !(entity.equals(mc.player))) return;

        ticks = delay.get();
        if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
            FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
            totems = result.count();
            equipTotemToOffhand(result);
        }
    }

    private void equipTotemToOffhand(FindItemResult result) {
        if (mc.player == null || !result.found() || result.isOffhand()) return;

        // Dọn sạch con trỏ chuột nếu trước đó bị dính ghost item lơ lửng
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
            FindItemResult empty = InvUtils.findEmpty();
            if (empty.found()) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, SlotUtils.indexToId(empty.slot()), 0, SlotActionType.PICKUP, mc.player);
            }
        }

        int slotId = SlotUtils.indexToId(result.slot());
        if (slotId != -1) {
            // Sử dụng cơ chế SWAP phím F chuẩn vanilla (button = 40)
            // Đổi trực tiếp 1 packet nguyên tử, không nhấc chuột lên con trỏ -> không bao giờ bị ghost totem
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slotId, 40, SlotActionType.SWAP, mc.player);
        }
    }

    public boolean isLocked() {
        return isActive() && locked;
    }

    @Override
    public String getInfoString() {
        return String.valueOf(totems);
    }

    public enum Mode {
        Smart,
        Strict
    }
}
