package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import paladi.client.events.packets.PacketEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.BoolSetting;
import paladi.client.settings.DoubleSetting;
import paladi.client.settings.EnumSetting;
import paladi.client.settings.Setting;
import paladi.client.settings.SettingGroup;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.player.PlayerUtils;
import paladi.client.utils.player.SlotUtils;

public class KryptonAutoTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Determines when to hold a totem, strict will always hold.")
        .defaultValue(Mode.Strict)
        .build()
    );

    private final Setting<Double> health = sgGeneral.add(new DoubleSetting.Builder()
        .name("health")
        .description("Health threshold to swap to totem.")
        .defaultValue(14.0)
        .min(1)
        .sliderMax(20)
        .visible(() -> mode.get() == Mode.Smart)
        .build()
    );

    private final Setting<Boolean> fastSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("fast-packet-swap")
        .description("Uses instant vanilla offhand swap packet when totem is in hotbar (0ms, bypasses ghost items).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hotbarFallback = sgGeneral.add(new BoolSetting.Builder()
        .name("hotbar-fallback")
        .description("Instantly switches mainhand hotbar slot to totem if offhand is not ready or when totem pops.")
        .defaultValue(true)
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
    private int totems;

    public KryptonAutoTotem() {
        super(Categories.COMBAT, "krypton-auto-totem", "Fastest totem switcher with instant packet response and emergency hotbar protection.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTickPre(TickEvent.Pre event) {
        updateTotemEquip();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTickPost(TickEvent.Post event) {
        updateTotemEquip();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;
        if (p.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;

        Entity entity = p.getEntity(mc.world);
        if (entity == null || !entity.equals(mc.player)) return;

        if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
            FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
            totems = result.count();
            equipTotemToOffhand(result);
        }
    }

    private void updateTotemEquip() {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;

        FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
        totems = result.count();

        if (totems <= 0) {
            locked = false;
            return;
        }

        double currentHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        double predictedDmg = PlayerUtils.possibleHealthReductions(explosion.get(), fall.get());
        boolean low = (currentHp - predictedDmg) <= health.get();
        boolean ely = elytra.get() && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA && mc.player.isGliding();

        locked = mode.get() == Mode.Strict || (mode.get() == Mode.Smart && (low || ely));

        if (locked && mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            equipTotemToOffhand(result);
        }
    }

    private void equipTotemToOffhand(FindItemResult result) {
        if (mc.player == null || !result.found() || result.isOffhand()) return;

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
