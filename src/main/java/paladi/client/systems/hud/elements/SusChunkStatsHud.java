package paladi.client.systems.hud.elements;

import paladi.client.systems.hud.Hud;
import paladi.client.systems.hud.HudElement;
import paladi.client.systems.hud.HudElementInfo;
import paladi.client.systems.hud.HudRenderer;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.krypton.SusChunkFinder;
import paladi.client.utils.render.color.Color;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import static paladi.client.PaladiClient.mc;

public class SusChunkStatsHud extends HudElement {
    public static final HudElementInfo<SusChunkStatsHud> INFO = new HudElementInfo<>(
        Hud.GROUP,
        "sus-chunk-stats",
        "Sus Chunk Stats",
        "Displays real-time suspicious chunk scan data.",
        SusChunkStatsHud::new
    );

    private static final Color BG_COLOR = new Color(0, 0, 0, 150);
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color YELLOW = new Color(255, 255, 100);
    private static final Color RED = new Color(255, 80, 80);

    public SusChunkStatsHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        setSize(160, 55);

        SusChunkFinder finder = Modules.get().get(SusChunkFinder.class);
        if (finder == null) return;

        ChunkPos current = mc.player.getChunkPos();
        SusChunkFinder.ChunkStats stats = finder.getStats(current.x, current.z);
        
        boolean sus = stats != null && stats.threat.ordinal() >= SusChunkFinder.ThreatLevel.HIGH.ordinal();
        long t = System.currentTimeMillis();

        // Speed calculation using velocity
        Vec3d vel = mc.player.getVelocity();
        double bps = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0;
        
        // Background
        renderer.quad(x, y, getWidth(), getHeight() + (sus ? 12 : 0), BG_COLOR);

        // Header
        renderer.text("SUS SCANNER v2", x + 5, y + 5, RED, false, 0.7);

        // XYZ Info
        String chunkText = String.format("XYZ: %d, %d, %d", (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
        renderer.text(chunkText, x + 5, y + 15, WHITE, false, 0.8);

        // Speed Info
        String speedText = String.format("Speed: %.2f bps", bps);
        renderer.text(speedText, x + 5, y + 25, YELLOW, false, 0.8);

        if (stats != null) {
            String scoreText = "Score: " + stats.baseScore + "% (" + stats.threat.label + ")";
            renderer.text(scoreText, x + 5, y + 35, stats.renderColor, false, 0.8);

            if (sus) {
                float pulse = 0.7f + 0.3f * (float) Math.sin(t / 200.0);
                renderer.text("⚠ SUS CHUNK DETECTED!", x + 5, y + 47, RED.copy().a((int)(pulse * 255)), false, 0.8);
            }
        } else {
            renderer.text("Scanning...", x + 5, y + 35, new Color(150, 150, 150), false, 0.8);
        }

        // Slow down warning
        if (bps > 20.0) {
            float pulse = 0.6f + 0.4f * (float) Math.sin(t / 150.0);
            renderer.text("Slow down!", x + getWidth() / 2.0 - 30, y + getHeight() + 15, RED.copy().a((int)(pulse * 255)), false, 1.2);
        }
    }
}
