package paladi.client.systems.modules.krypton;

import paladi.client.events.render.Render2DEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static paladi.client.PaladiClient.mc;

public class KryptonArrayList extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the array list.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(2.0)
        .build()
    );

    public KryptonArrayList() {
        super(Categories.CLIENT, "KRYPTON+", "Renders a professional list of active modules.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        List<Module> activeModules = Modules.get().getActive().stream()
            .sorted(Comparator.comparingInt(m -> -mc.textRenderer.getWidth(m.title)))
            .collect(Collectors.toList());

        float s = scale.get().floatValue();
        int y = 10;
        int xOffset = 5;

        event.drawContext.getMatrices().pushMatrix();
        event.drawContext.getMatrices().scale(s, s);

        int count = 0;
        for (Module m : activeModules) {
            int width = mc.textRenderer.getWidth(m.title);
            int x = (int) ((event.screenWidth / s) - width - xOffset);
            
            // Rainbow color effect
            int color = java.awt.Color.HSBtoRGB((System.currentTimeMillis() + (count * 100)) % 5000 / 5000f, 0.7f, 1f);
            
            event.drawContext.drawTextWithShadow(mc.textRenderer, m.title, x, (int) (y / s), color);
            y += (int) (10 * s);
            count++;
        }

        event.drawContext.getMatrices().popMatrix();
    }
}
