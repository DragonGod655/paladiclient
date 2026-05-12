package paladi.client.systems.hud.elements;

import paladi.client.PaladiClient;
import paladi.client.settings.*;
import paladi.client.systems.hud.Hud;
import paladi.client.systems.hud.HudElement;
import paladi.client.systems.hud.HudElementInfo;
import paladi.client.systems.hud.HudRenderer;
import paladi.client.utils.render.color.Color;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class ChibiWaifuHud extends HudElement {
    public static final HudElementInfo<ChibiWaifuHud> INFO = new HudElementInfo<>(Hud.GROUP, "chibi-waifu", "Chibi Waifu", "Your cute walking anime assistant.", ChibiWaifuHud::new);

    private static final Identifier TEXTURE = PaladiClient.identifier("textures/chibi.png");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of your Chibi Waifu.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderRange(0.1, 2)
        .build()
    );

    private final Setting<Boolean> walking = sgGeneral.add(new BoolSetting.Builder()
        .name("walking-animation")
        .description("Makes the Chibi walk around.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> crop = sgGeneral.add(new DoubleSetting.Builder()
        .name("crop-factor")
        .description("Crop the edges of the texture to remove borders.")
        .defaultValue(0.0)
        .min(0)
        .max(0.4)
        .build()
    );

    private final Setting<Double> brightness = sgGeneral.add(new DoubleSetting.Builder()
        .name("brightness")
        .description("Brightness of the Chibi.")
        .defaultValue(1.0)
        .min(0.5)
        .max(2.0)
        .build()
    );

    private long lastTime = System.currentTimeMillis();
    private double walkPos = 0;
    
    private String currentMessage = "";
    private long messageTime = 0;

    public ChibiWaifuHud() {
        super(INFO);
    }

    public void setMessage(String msg) {
        this.currentMessage = msg;
        this.messageTime = System.currentTimeMillis();
    }

    @Override
    public void render(HudRenderer renderer) {
        long now = System.currentTimeMillis();
        float delta = (now - lastTime) / 1000f;
        lastTime = now;

        if (walking.get()) {
            walkPos += delta * 0.5;
        }

        double offsetX = walking.get() ? Math.sin(walkPos) * 20 : 0;
        double offsetY = walking.get() ? Math.abs(Math.cos(walkPos * 2)) * 5 : 0;

        double w = 200 * scale.get();
        double h = 200 * scale.get();

        setSize(w + 40, h + 10);

        // Single frame Bongo Cat logic
        double c = crop.get();
        double u1 = c;
        double u2 = 1.0 - c;
        double v1 = c;
        double v2 = 1.0 - c;

        // Color with brightness adjustment
        int br = (int) (255 * brightness.get());
        br = MathHelper.clamp(br, 0, 255);
        Color chibiColor = new Color(br, br, br, 255);

        // Draw Bongo Cat
        renderer.texture(TEXTURE, x + 20 + offsetX, y + 5 - offsetY, w, h, u1, v1, u2, v2, chibiColor);

        // Dialogue bubble
        if (!currentMessage.isEmpty() && now - messageTime < 5000) {
            String message = currentMessage;
            double textWidth = HudRenderer.INSTANCE.textWidth(message, false);
            double bubbleWidth = textWidth + 10;
            double bubbleHeight = 16;
            double bubbleX = x + (w - bubbleWidth) / 2;
            double bubbleY = y - 20;

            HudRenderer.INSTANCE.quad(bubbleX, bubbleY, bubbleWidth, bubbleHeight, new Color(0, 0, 0, 180));
            HudRenderer.INSTANCE.text(message, bubbleX + 5, bubbleY + 4, Color.WHITE, false);
        } else {
            currentMessage = "";
        }
    }
}
