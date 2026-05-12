package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.events.render.Render3DEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import paladi.client.utils.render.color.Color;
import paladi.client.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static paladi.client.PaladiClient.mc;

public class JumpCircles extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the circles.")
        .defaultValue(new SettingColor(0, 255, 255, 200))
        .build()
    );

    private final Setting<Double> expandSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("expand-speed")
        .description("How fast the circles expand.")
        .defaultValue(0.1)
        .min(0.01)
        .sliderMax(0.5)
        .build()
    );

    private final Setting<Integer> lifetime = sgGeneral.add(new IntSetting.Builder()
        .name("lifetime")
        .description("How many ticks the circles last.")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final List<Circle> circles = new ArrayList<>();
    private boolean wasOnGround = true;

    public JumpCircles() {
        super(Categories.RENDER, "jump-circles", "Renders professional circles when you jump.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (wasOnGround && !mc.player.isOnGround()) {
            circles.add(new Circle(mc.player.getEntityPos()));
        }

        wasOnGround = mc.player.isOnGround();
        circles.removeIf(Circle::tick);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Circle circle : circles) {
            renderCircle(event, circle);
        }
    }

    private void renderCircle(Render3DEvent event, Circle circle) {
        double radius = circle.age * expandSpeed.get();
        int alpha = (int) (color.get().a * (1.0 - (double) circle.age / lifetime.get()));
        Color c = new Color(color.get().r, color.get().g, color.get().b, alpha);

        double x = circle.pos.x;
        double y = circle.pos.y + 0.05;
        double z = circle.pos.z;

        int points = 30;
        for (int i = 0; i < points; i++) {
            double angle1 = Math.toRadians((360.0 / points) * i);
            double angle2 = Math.toRadians((360.0 / points) * (i + 1));

            double x1 = x + Math.cos(angle1) * radius;
            double z1 = z + Math.sin(angle1) * radius;
            double x2 = x + Math.cos(angle2) * radius;
            double z2 = z + Math.sin(angle2) * radius;

            event.renderer.line(x1, y, z1, x2, y, z2, c);
        }
    }

    private class Circle {
        public final Vec3d pos;
        public int age = 0;

        public Circle(Vec3d pos) {
            this.pos = pos;
        }

        public boolean tick() {
            age++;
            return age >= lifetime.get();
        }
    }
}
