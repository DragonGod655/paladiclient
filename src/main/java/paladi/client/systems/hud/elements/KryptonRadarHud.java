package paladi.client.systems.hud.elements;

import paladi.client.systems.hud.Hud;
import paladi.client.systems.hud.HudElement;
import paladi.client.systems.hud.HudElementInfo;
import paladi.client.systems.hud.HudRenderer;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.krypton.SusChunkFinder;
import paladi.client.utils.render.color.Color;
import net.minecraft.util.math.ChunkPos;

import static paladi.client.PaladiClient.mc;

public class KryptonRadarHud extends HudElement {
    public static final HudElementInfo<KryptonRadarHud> INFO = new HudElementInfo<>(
        Hud.GROUP, 
        "krypton-radar-v5", 
        "AA Krypton Radar",
        "Authentic Krypton Engine chunk radar.", 
        KryptonRadarHud::new
    );

    private static final Color BG_COLOR = new Color(5, 10, 15, 200);
    private static final Color CYAN = new Color(0, 220, 255);
    private static final Color DARK_CYAN = new Color(0, 100, 120);
    private static final Color WHITE = new Color(255, 255, 255);

    public KryptonRadarHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        setSize(120, 150);

        double rx = (double) x;
        double ry = (double) y;
        double rw = (double) getWidth();
        double rh = (double) getHeight();

        // Authentic Header Bar
        renderer.quad(rx, ry, rw, rh, BG_COLOR);
        renderer.line(rx, ry, rx + rw, ry, CYAN);
        renderer.line(rx, ry + 15, rx + rw, ry + 15, DARK_CYAN);
        
        renderer.text("KRYPTON RADAR", rx + 5, ry + 4, CYAN, false, 0.7);
        renderer.text("v1.0", rx + rw - 25, ry + 4, new Color(150, 150, 150), false, 0.6);

        SusChunkFinder finder = Modules.get().get(SusChunkFinder.class);
        if (finder == null || !finder.isActive()) {
            renderer.text("ENGINE OFFLINE", rx + rw/2 - renderer.textWidth("ENGINE OFFLINE", false, 0.8)/2, ry + rh/2, new Color(100, 100, 100), false, 0.8);
            return;
        }

        ChunkPos current = mc.player.getChunkPos();
        double slotSize = 28.0;
        double startX = rx + (rw - slotSize * 3.0) / 2.0;
        double startY = ry + 25.0;

        // 3x3 Radar Grid
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = current.x + dx;
                int nz = current.z + dz;
                
                SusChunkFinder.ChunkStats stats = finder.getStats(nx, nz);
                Color color = (stats != null) ? stats.renderColor : new Color(30, 40, 50);
                
                double px = startX + (dx + 1) * slotSize;
                double py = startY + (dz + 1) * slotSize;

                renderer.quad(px + 2, py + 2, slotSize - 4, slotSize - 4, color.copy().a(180));
                
                // Crosshair for current chunk
                if (dx == 0 && dz == 0) {
                    renderer.quad(px + slotSize/2 - 1, py + 5, 2, slotSize - 10, WHITE);
                    renderer.quad(px + 5, py + slotSize/2 - 1, slotSize - 10, 2, WHITE);
                }
            }
        }

        // Stats Footer
        SusChunkFinder.ChunkStats currentStats = finder.getStats(current.x, current.z);
        if (currentStats != null) {
            String threatStr = currentStats.threat.label + " THREAT";
            renderer.text(threatStr, rx + 10, ry + rh - 35, currentStats.renderColor, false, 0.7);
            
            String scoreStr = "Base: " + currentStats.baseScore + "%";
            renderer.text(scoreStr, rx + 10, ry + rh - 25, WHITE, false, 0.6);
            
            String entStr = "E: " + currentStats.entities + " | T: " + currentStats.tiles;
            renderer.text(entStr, rx + 10, ry + rh - 15, new Color(150, 150, 150), false, 0.6);
        }

        // Coordinates
        String coords = (int) mc.player.getX() + ", " + (int) mc.player.getY() + ", " + (int) mc.player.getZ();
        renderer.text(coords, rx + rw - renderer.textWidth(coords, false, 0.6) - 5, ry + rh - 15, CYAN, false, 0.6);
    }
}
