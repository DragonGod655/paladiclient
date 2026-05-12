package paladi.client.systems.modules.krypton;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import paladi.client.events.render.Render3DEvent;
import paladi.client.events.world.ChunkDataEvent;
import paladi.client.renderer.ShapeMode;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public class ShaftFinder extends Module {
    public static class Shaft {
        public final int x, z, yStart, yEnd;

        public Shaft(int x, int z, int yStart, int yEnd) {
            this.x = x;
            this.z = z;
            this.yStart = yStart;
            this.yEnd = yEnd;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> minHeight = sgGeneral.add(new IntSetting.Builder()
        .name("min-height")
        .description("Minimum consecutive air blocks to count as a shaft.")
        .defaultValue(10)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to start searching for shafts.")
        .defaultValue(64)
        .sliderRange(-64, 319)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the highlighted shaft.")
        .defaultValue(new SettingColor(0, 255, 255, 100))
        .build()
    );

    private final Long2ObjectMap<List<Shaft>> shafts = new Long2ObjectOpenHashMap<>();

    public ShaftFinder() {
        super(Categories.MISC, "shaft-finder", "Detects and highlights vertical mining shafts.");
    }

    @Override
    public void onActivate() {
        shafts.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        WorldChunk chunk = event.chunk();
        ChunkPos pos = chunk.getPos();
        List<Shaft> chunkShafts = new ArrayList<>();

        int xOffset = pos.x * 16;
        int zOffset = pos.z * 16;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int consecutiveAir = 0;
                int startY = -1;

                for (int y = maxY.get(); y >= -64; y--) {
                    mutable.set(xOffset + x, y, zOffset + z);
                    if (chunk.getBlockState(mutable).isAir()) {
                        if (consecutiveAir == 0) startY = y;
                        consecutiveAir++;
                    } else {
                        if (consecutiveAir >= minHeight.get()) {
                            // Check surrounding blocks to confirm it's a 1x1 or 1x2 hole, not a huge open area
                            boolean isSmall = true;
                            // Check middle point surrounding
                            int midY = startY - consecutiveAir / 2;
                            int airNeighbors = 0;
                            
                            for (int dx = -1; dx <= 1; dx++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    if (dx == 0 && dz == 0) continue;
                                    mutable.set(xOffset + x + dx, midY, zOffset + z + dz);
                                    if (chunk.getBlockState(mutable).isAir()) airNeighbors++;
                                }
                            }
                            
                            // If more than 2 neighbors are air, it's probably a natural cave or room
                            if (airNeighbors <= 2) {
                                chunkShafts.add(new Shaft(xOffset + x, zOffset + z, startY, y + 1));
                            }
                        }
                        consecutiveAir = 0;
                    }
                }
                
                if (consecutiveAir >= minHeight.get()) {
                    chunkShafts.add(new Shaft(xOffset + x, zOffset + z, startY, -63));
                }
            }
        }

        if (!chunkShafts.isEmpty()) {
            synchronized (shafts) {
                shafts.put(pos.toLong(), chunkShafts);
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        synchronized (shafts) {
            for (List<Shaft> chunkList : shafts.values()) {
                for (Shaft shaft : chunkList) {
                    event.renderer.box(shaft.x, shaft.yEnd, shaft.z, shaft.x + 1, shaft.yStart + 1, shaft.z + 1, color.get(), color.get(), ShapeMode.Both, 0);
                }
            }
        }
    }
}
