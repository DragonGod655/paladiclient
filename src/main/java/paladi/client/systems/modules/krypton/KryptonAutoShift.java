package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import static paladi.client.PaladiClient.mc;

public class KryptonAutoShift extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyAfk = sgGeneral.add(new BoolSetting.Builder()
        .name("only-afk")
        .description("Chỉ tự động shift khi bạn đang AFK. Tắt đi để luôn luôn ngồi.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> afkDelay = sgGeneral.add(new IntSetting.Builder()
        .name("afk-delay-seconds")
        .description("Thời gian (giây) không di chuyển để được tính là AFK.")
        .defaultValue(5)
        .min(1)
        .sliderMax(60)
        .visible(onlyAfk::get)
        .build()
    );

    private Vec3d lastPos;
    private float lastYaw, lastPitch;
    private int afkTimer = 0;
    private boolean isAfk = false;

    public KryptonAutoShift() {
        super(Categories.MISC, "krypton-auto-shift", "Tự động ngồi để che tên khi AFK.");
    }

    @Override
    public void onActivate() {
        resetAfk();
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null && mc.options.sneakKey != null) {
            mc.options.sneakKey.setPressed(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (onlyAfk.get()) {
            updateAfkStatus();
            if (isAfk) {
                mc.options.sneakKey.setPressed(true);
            }
        } else {
            mc.options.sneakKey.setPressed(true);
        }
    }

    private void updateAfkStatus() {
        Vec3d currentPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        if (lastPos != null && currentPos.equals(lastPos) && currentYaw == lastYaw && currentPitch == lastPitch) {
            afkTimer++;
            if (afkTimer >= afkDelay.get() * 20) {
                isAfk = true;
            }
        } else {
            resetAfk();
        }

        lastPos = currentPos;
        lastYaw = currentYaw;
        lastPitch = currentPitch;
    }

    private void resetAfk() {
        afkTimer = 0;
        isAfk = false;
        if (mc.options != null && mc.options.sneakKey != null) {
            mc.options.sneakKey.setPressed(false);
        }
    }
}
