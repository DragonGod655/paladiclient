package paladi.client.systems.modules.krypton;

import paladi.client.events.render.Render2DEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import paladi.client.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import static paladi.client.PaladiClient.mc;

public class KryptonWatermark extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the watermark.")
        .defaultValue(1.5)
        .min(1)
        .sliderMax(5)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the watermark.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    public KryptonWatermark() {
        super(Categories.CLIENT, "krypton-watermark", "Displays KRYPTON+ watermark.");
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        String text = "KRYPTON+ 1.21.11";
        float s = scale.get().floatValue();
        
        int x = 10;
        int y = 10;

        event.drawContext.getMatrices().pushMatrix();
        event.drawContext.getMatrices().scale(s, s);
        
        event.drawContext.drawTextWithShadow(mc.textRenderer, text, (int) (x / s), (int) (y / s), color.get().getPacked());
        
        // Render FPS/Ping below
        String info = "FPS: " + mc.getCurrentFps() + " | Ping: " + (mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null ? mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency() : 0);
        event.drawContext.getMatrices().scale(0.5f, 0.5f);
        event.drawContext.drawTextWithShadow(mc.textRenderer, info, (int) (x / (s * 0.5f)), (int) ((y + (12 * s)) / (s * 0.5f)), color.get().getPacked());

        event.drawContext.getMatrices().popMatrix();
    }
}
