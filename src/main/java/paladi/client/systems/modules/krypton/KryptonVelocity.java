package paladi.client.systems.modules.krypton;

import paladi.client.events.packets.PacketEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;

import static paladi.client.PaladiClient.mc;

public class KryptonVelocity extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> horizontal = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal")
        .description("Horizontal velocity multiplier.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderMax(1.0)
        .build()
    );

    private final Setting<Double> vertical = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical")
        .description("Vertical velocity multiplier.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderMax(1.0)
        .build()
    );

    public KryptonVelocity() {
        super(Categories.COMBAT, "krypton-velocity", "Zero knockback for pro PvP.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;

        if (event.packet instanceof EntityVelocityUpdateS2CPacket packet) {
            if (packet.getEntityId() == mc.player.getId()) {
                double h = horizontal.get();
                double v = vertical.get();

                if (h == 0 && v == 0) event.cancel();
            }
        } else if (event.packet instanceof ExplosionS2CPacket) {
            if (horizontal.get() == 0 && vertical.get() == 0) event.cancel();
        }
    }
}
