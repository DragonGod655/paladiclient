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
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class TotemInventory extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<AutoTotem.Mode> mode = sgGeneral.add(new EnumSetting.Builder<AutoTotem.Mode>()
        .name("mode")
        .description("Determines when to hold a totem, strict will always hold.")
        .defaultValue(AutoTotem.Mode.Smart)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The ticks between slot movements. Higher values prevent ghost items.")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Integer> health = sgGeneral.add(new IntSetting.Builder()
        .name("health")
        .description("The health to hold a totem at.")
        .defaultValue(10)
        .range(0, 36)
        .sliderMax(36)
        .visible(() -> mode.get() == AutoTotem.Mode.Smart)
        .build()
    );

    private final Setting<Boolean> elytra = sgGeneral.add(new BoolSetting.Builder()
        .name("elytra")
        .description("Will always hold a totem when flying with elytra.")
        .defaultValue(true)
        .visible(() -> mode.get() == AutoTotem.Mode.Smart)
        .build()
    );

    private final Setting<Boolean> fall = sgGeneral.add(new BoolSetting.Builder()
        .name("fall")
        .description("Will hold a totem when fall damage could kill you.")
        .defaultValue(true)
        .visible(() -> mode.get() == AutoTotem.Mode.Smart)
        .build()
    );

    private final Setting<Boolean> explosion = sgGeneral.add(new BoolSetting.Builder()
        .name("explosion")
        .description("Will hold a totem when explosion damage could kill you.")
        .defaultValue(true)
        .visible(() -> mode.get() == AutoTotem.Mode.Smart)
        .build()
    );

    private final Setting<Boolean> bypassAnticheat = sgGeneral.add(new BoolSetting.Builder()
        .name("bypass-anticheat")
        .description("Briefly fakes/opens inventory to bypass anticheats like Grim or Donut SMP.")
        .defaultValue(true)
        .build()
    );

    public boolean locked;
    private int totems, ticks;
    
    private enum State {
        IDLE,
        OPEN_INVENTORY,
        MOVE_ITEM,
        CLOSE_INVENTORY
    }
    private State currentState = State.IDLE;
    private int actionTimer = 0;
    private int targetSlot = -1;
    private boolean wasSprinting = false;

    public TotemInventory() {
        super(Categories.Combat, "totem-inventory", "Equips totem safely from inventory to bypass strict anticheats.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) {
        FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
        totems = result.count();

        if (totems <= 0) {
            locked = false;
            currentState = State.IDLE;
        } else {
            boolean low = mc.player.getHealth() + mc.player.getAbsorptionAmount() - PlayerUtils.possibleHealthReductions(explosion.get(), fall.get()) <= health.get();
            boolean ely = elytra.get() && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA && mc.player.isGliding();

            locked = mode.get() == AutoTotem.Mode.Strict || (mode.get() == AutoTotem.Mode.Smart && (low || ely));

            if (locked && mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                if (currentState == State.IDLE) {
                    currentState = bypassAnticheat.get() ? State.OPEN_INVENTORY : State.MOVE_ITEM;
                    targetSlot = result.slot();
                    actionTimer = 0;
                }
            } else if (currentState != State.CLOSE_INVENTORY && currentState != State.MOVE_ITEM) {
                currentState = State.IDLE;
            }
        }

        if (currentState != State.IDLE) {
            if (actionTimer < delay.get()) {
                actionTimer++;
                return;
            }

            switch (currentState) {
                case OPEN_INVENTORY:
                    if (mc.currentScreen == null) {
                        wasSprinting = mc.player.isSprinting();
                        if (wasSprinting) {
                            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                        }
                        mc.setScreen(new InventoryScreen(mc.player));
                    }
                    currentState = State.MOVE_ITEM;
                    actionTimer = 0;
                    break;
                case MOVE_ITEM:
                    InvUtils.move().from(targetSlot).toOffhand();
                    currentState = bypassAnticheat.get() && mc.currentScreen instanceof InventoryScreen ? State.CLOSE_INVENTORY : State.IDLE;
                    actionTimer = 0;
                    break;
                case CLOSE_INVENTORY:
                    if (mc.currentScreen instanceof InventoryScreen) {
                        mc.setScreen(null);
                        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                    if (wasSprinting) {
                        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                    }
                    currentState = State.IDLE;
                    break;
                case IDLE:
                    break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;
        if (p.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;

        Entity entity = p.getEntity(mc.world);
        if (entity == null || !(entity.equals(mc.player))) return;

        currentState = State.IDLE;
        actionTimer = 0;
    }

    public boolean isLocked() {
        return isActive() && locked;
    }

    @Override
    public String getInfoString() {
        return String.valueOf(totems);
    }
}
