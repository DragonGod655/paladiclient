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

public class NetheriteFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(139, 69, 19, 150)).build()
    );

    private final Setting<Integer> renderHeight = sgGeneral.add(new IntSetting.Builder()
        .name("height").defaultValue(15).min(0).sliderMax(128).build()
    );

    private final Long2IntMap chunks = new Long2IntOpenHashMap();

    public NetheriteFinder() {
        super(Categories.DONUT, "netherite-finder", "Highlights chunks containing ancient debris in the Nether.");
    }

    @Override
    public void onDeactivate() {
        chunks.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        WorldChunk chunk = event.chunk();
        int count = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 8; y <= 119; y++) {
                    pos.set(chunk.getPos().x * 16 + x, y, chunk.getPos().z * 16 + z);
                    if (chunk.getBlockState(pos).getBlock() == Blocks.ANCIENT_DEBRIS) {
                        count++;
                    }
                }
            }
        }

        if (count > 0) {
            chunks.put(ChunkPos.toLong(chunk.getPos().x, chunk.getPos().z), count);
            info("Ancient debris chunk at X:%d Z:%d (%d found)", chunk.getPos().x * 16, chunk.getPos().z * 16, count);
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
