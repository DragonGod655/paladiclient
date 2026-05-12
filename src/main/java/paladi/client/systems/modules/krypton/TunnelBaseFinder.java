package paladi.client.systems.modules.krypton;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import paladi.client.events.render.Render3DEvent;
import paladi.client.events.world.ChunkDataEvent;
import paladi.client.renderer.ShapeMode;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import static paladi.client.PaladiClient.mc;

public class TunnelBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build()
    );
    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(255, 100, 0, 130)).build()
    );
    private final Setting<Integer> renderHeight = sgGeneral.add(new IntSetting.Builder()
        .name("height").defaultValue(10).min(0).sliderMax(128).build()
    );
    private final Setting<Integer> minTunnelBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("min-tunnel-blocks").description("Min air blocks in a line to count as tunnel.").defaultValue(8).min(3).sliderMax(32).build()
    );

    private final Long2IntMap chunks = new Long2IntOpenHashMap();

    public TunnelBaseFinder() {
        super(Categories.DONUT, "tunnel-base-finder", "Detects strip mine tunnels to find player bases.");
    }

    @Override
    public void onDeactivate() { chunks.clear(); }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        WorldChunk chunk = event.chunk();
        int tunnelScore = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // Scan for horizontal tunnels (1x2 air corridors underground)
        for (int y = -60; y < 30; y++) {
            for (int z = 0; z < 16; z++) {
                int airStreak = 0;
                for (int x = 0; x < 16; x++) {
                    pos.set(chunk.getPos().x * 16 + x, y, chunk.getPos().z * 16 + z);
                    BlockState state = chunk.getBlockState(pos);
                    pos.set(chunk.getPos().x * 16 + x, y + 1, chunk.getPos().z * 16 + z);
                    BlockState above = chunk.getBlockState(pos);
                    if (state.isAir() && above.isAir()) airStreak++;
                    else {
                        if (airStreak >= minTunnelBlocks.get()) tunnelScore += airStreak;
                        airStreak = 0;
                    }
                }
                if (airStreak >= minTunnelBlocks.get()) tunnelScore += airStreak;
            }

            // Also check Z-direction tunnels
            for (int x = 0; x < 16; x++) {
                int airStreak = 0;
                for (int z2 = 0; z2 < 16; z2++) {
                    pos.set(chunk.getPos().x * 16 + x, y, chunk.getPos().z * 16 + z2);
                    BlockState state = chunk.getBlockState(pos);
                    pos.set(chunk.getPos().x * 16 + x, y + 1, chunk.getPos().z * 16 + z2);
                    BlockState above = chunk.getBlockState(pos);
                    if (state.isAir() && above.isAir()) airStreak++;
                    else {
                        if (airStreak >= minTunnelBlocks.get()) tunnelScore += airStreak;
                        airStreak = 0;
                    }
                }
                if (airStreak >= minTunnelBlocks.get()) tunnelScore += airStreak;
            }
        }

        if (tunnelScore > 0) {
            chunks.put(ChunkPos.toLong(chunk.getPos().x, chunk.getPos().z), tunnelScore);
            info("Tunnel detected at X:%d Z:%d (score: %d)", chunk.getPos().x * 16, chunk.getPos().z * 16, tunnelScore);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Long2IntMap.Entry entry : chunks.long2IntEntrySet()) {
            int cx = (int) entry.getLongKey();
            int cz = (int) (entry.getLongKey() >> 32);
            int ySurface = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cx * 16 + 8, cz * 16 + 8);
            double h = ySurface + 0.1;
            event.renderer.box(cx * 16, ySurface, cz * 16, cx * 16 + 16, h, cz * 16 + 16,
                color.get(), color.get(), shapeMode.get(), 0);
        }
    }
}
