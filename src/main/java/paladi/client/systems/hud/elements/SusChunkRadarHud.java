package paladi.client.systems.hud.elements;

import paladi.client.settings.DoubleSetting;
import paladi.client.settings.IntSetting;
import paladi.client.settings.Setting;
import paladi.client.settings.SettingGroup;
import paladi.client.systems.hud.Hud;
import paladi.client.systems.hud.HudElement;
import paladi.client.systems.hud.HudElementInfo;
import paladi.client.systems.hud.HudRenderer;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.krypton.SusChunkFinder;
import paladi.client.utils.render.color.Color;
import net.minecraft.util.math.ChunkPos;

import static paladi.client.PaladiClient.mc;

public class SusChunkRadarHud extends HudElement {
    public static final HudElementInfo<SusChunkRadarHud> INFO = new HudElementInfo<>(
        Hud.GROUP,
        "sus-chunk-radar",
        "Sus Chunk Radar",
        "A sleek circular radar for locating suspicious chunks.",
        SusChunkRadarHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("The range of chunks to display.")
        .defaultValue(7)
        .min(1)
        .sliderRange(1, 15)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale of the radar.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 2.0)
        .build()
    );

    private static final Color BG_COLOR = new Color(0, 0, 0, 100);
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color RED = new Color(255, 30, 60);
    private static final Color GRAY = new Color(200, 200, 200, 150);

    public SusChunkRadarHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double s = scale.get();
        int mapSize = (int) (140 * s);
        int r = range.get();
        setSize(mapSize, mapSize);

        SusChunkFinder finder = Modules.get().get(SusChunkFinder.class);
        if (finder == null) return;

        double centerX = x + mapSize / 2.0;
        double centerY = y + mapSize / 2.0;
        double radius = mapSize / 2.0;

        // 1. Draw smooth circular background
        drawCircle(renderer, centerX, centerY, radius, BG_COLOR);
        
        // 2. Draw Grid Lines (Subtle)
        drawGrid(renderer, centerX, centerY, radius, mapSize / (r * 2.0 + 1));

        // 3. Draw Chunks
        ChunkPos playerChunk = mc.player.getChunkPos();
        double cellSize = (double) mapSize / (r * 2 + 1);
        long t = System.currentTimeMillis();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int nx = playerChunk.x + dx;
                int nz = playerChunk.z + dz;
                
                SusChunkFinder.ChunkStats stats = finder.getStats(nx, nz);
                // FIX: Hide LOW threat chunks on map to reduce clutter
                if (stats == null || stats.threat == SusChunkFinder.ThreatLevel.LOW) continue;
                
                double rx = centerX + dx * cellSize - cellSize / 2.0;
                double ry = centerY + dz * cellSize - cellSize / 2.0;
                
                // Circular clipping check
                double dist = Math.sqrt(Math.pow(rx + cellSize/2.0 - centerX, 2) + Math.pow(ry + cellSize/2.0 - centerY, 2));
                if (dist > radius - 2) continue;
                
                Color color = stats.renderColor.copy();
                if (stats.threat.ordinal() >= SusChunkFinder.ThreatLevel.HIGH.ordinal()) {
                    float pulse = 0.6f + 0.3f * (float) Math.sin(t / 250.0 + dx + dz);
                    color = RED.copy().a((int)(pulse * 255));
                } else {
                    color.a(180);
                }
                
                renderer.quad(rx + 1, ry + 1, cellSize - 2, cellSize - 2, color);
            }
        }

        // 4. Compass Markers (N, S, W, E)
        renderer.text("N", centerX - 2.5, y + 4, RED, true, 0.7);
        renderer.text("S", centerX - 2.5, y + mapSize - 12, WHITE, true, 0.7);
        renderer.text("W", x + 4, centerY - 4, WHITE, true, 0.7);
        renderer.text("E", x + mapSize - 12, centerY - 4, WHITE, true, 0.7);

        // 5. Player Arrow (Fix: rotating triangle)
        drawArrow(renderer, centerX, centerY, mc.player.getYaw(), 4 * s, WHITE);

        // 6. Circular Outlines
        drawCircleOutline(renderer, centerX, centerY, radius, GRAY, 64);
        drawCircleOutline(renderer, centerX, centerY, radius - 1, new Color(0, 0, 0, 100), 64);
    }

    private void drawGrid(HudRenderer renderer, double cx, double cy, double r, double step) {
        Color gridColor = new Color(255, 255, 255, 20);
        for (double i = -r; i <= r; i += step) {
            // Horizontal
            renderer.line(cx - Math.sqrt(r*r - i*i), cy + i, cx + Math.sqrt(r*r - i*i), cy + i, gridColor);
            // Vertical
            renderer.line(cx + i, cy - Math.sqrt(r*r - i*i), cx + i, cy + Math.sqrt(r*r - i*i), gridColor);
        }
    }

    private void drawArrow(HudRenderer renderer, double x, double y, float yaw, double size, Color c) {
        double rad = Math.toRadians(yaw + 180);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        
        double x1 = x + (sin * size * 1.5);
        double y1 = y - (cos * size * 1.5);
        double x2 = x + (Math.sin(rad + 2.3) * size);
        double y2 = y - (Math.cos(rad + 2.3) * size);
        double x3 = x + (Math.sin(rad - 2.3) * size);
        double y3 = y - (Math.cos(rad - 2.3) * size);
        
        renderer.triangle(x1, y1, x2, y2, x3, y3, c);
    }

    private void drawCircle(HudRenderer renderer, double cx, double cy, double r, Color color) {
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians(i * (360.0 / segments));
            double a2 = Math.toRadians((i + 1) * (360.0 / segments));
            renderer.triangle(cx, cy, cx + Math.cos(a1) * r, cy + Math.sin(a1) * r, cx + Math.cos(a2) * r, cy + Math.sin(a2) * r, color);
        }
    }
    
    private void drawCircleOutline(HudRenderer renderer, double cx, double cy, double r, Color color, int segments) {
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians(i * (360.0 / segments));
            double a2 = Math.toRadians((i + 1) * (360.0 / segments));
            renderer.line(cx + Math.cos(a1) * r, cy + Math.sin(a1) * r, cx + Math.cos(a2) * r, cy + Math.sin(a2) * r, color);
        }
    }
}
