package paladi.client.systems.modules.krypton;

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

public class KryptonAutoTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Determines when to hold a totem, strict will always hold.")
        .defaultValue(Mode.Smart)
        .build()
    );

    private final Setting<Double> health = sgGeneral.add(new DoubleSetting.Builder()
        .name("health")
        .description("Health threshold to swap to totem.")
        .defaultValue(10.0)
        .min(1)
        .sliderMax(20)
        .visible(() -> mode.get() == Mode.Smart)
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

        // Instant packet response: Emergency swap hotbar if available
        if (hotbarFallback.get()) {
            FindItemResult hotbarTotem = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING);
            if (hotbarTotem.found() && hotbarTotem.isHotbar()) {
                InvUtils.swap(hotbarTotem.slot(), true);
            }
        }

        // Force immediate totem update on packet receive
        updateTotemEquip();
    }

    private void updateTotemEquip() {
        if (mc.player == null) return;

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

        if (locked) {
            // Hotbar emergency mainhand protection while offhand is empty/swapping
            if (hotbarFallback.get() && mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                FindItemResult hotbarTotem = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING);
                if (hotbarTotem.found() && hotbarTotem.isHotbar()) {
                    InvUtils.swap(hotbarTotem.slot(), true);
                }
            }

            // Move totem to offhand
            if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                InvUtils.move().from(result.slot()).toOffhand();
            }
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
