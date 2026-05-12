package paladi.client.systems.modules.render;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import paladi.client.events.render.Render3DEvent;
import paladi.client.events.world.ChunkDataEvent;
import paladi.client.renderer.ShapeMode;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import static paladi.client.PaladiClient.mc;

public class ChunkFinder extends Module {
    public enum Mode {
        BlockEntities,
        NewChunks
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How to find chunks.")
        .defaultValue(Mode.BlockEntities)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("base-chunk-color")
        .description("Color for old/saved chunks (Likely Base).")
        .defaultValue(new SettingColor(25, 225, 25, 150))
        .build()
    );

    private final Setting<Integer> minBlockEntities = sgGeneral.add(new IntSetting.Builder()
        .name("min-block-entities")
        .description("Only highlight chunks with at least this many block entities.")
        .defaultValue(1)
        .min(0)
        .sliderMax(100)
        .visible(() -> mode.get() == Mode.BlockEntities)
        .build()
    );

    private final Setting<Boolean> persist = sgGeneral.add(new BoolSetting.Builder()
        .name("persist")
        .description("Saves tracked chunks across sessions.")
        .defaultValue(true)
        .build()
    );

    private final LongSet chunks = new LongOpenHashSet();

    public ChunkFinder() {
        super(Categories.Render, "chunk-finder", "Highlights chunks based on various criteria.");
    }

    @Override
    public void onDeactivate() {
        if (!persist.get()) chunks.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        WorldChunk chunk = event.chunk();
        long key = chunk.getPos().toLong();

        if (mode.get() == Mode.BlockEntities) {
            if (chunk.getBlockEntityPositions().size() >= minBlockEntities.get()) {
                chunks.add(key);
            }
        } else {
            // Only highlight if it's an OLD chunk (Case 2) 
            if (chunk.getInhabitedTime() > 0 || !isNewChunk(chunk)) {
                chunks.add(key);
            }
        }
    }

    private boolean isNewChunk(WorldChunk chunk) {
        // Advanced Case 1 detection: 
        return chunk.getInhabitedTime() == 0 && chunk.getBlockEntityPositions().isEmpty();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (long chunkPosLong : chunks) {
            int cx = (int) chunkPosLong;
            int cz = (int) (chunkPosLong >> 32);
            int x = cx * 16;
            int z = cz * 16;

            int ySurface = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x + 8, z + 8);
            double h = ySurface + 0.1;
            event.renderer.box(x, ySurface, z, x + 16, h, z + 16, color.get(), color.get(), shapeMode.get(), 0);
        }
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        if (persist.get()) {
            NbtList list = new NbtList();
            for (long pos : chunks) list.add(NbtLong.of(pos));
            tag.put("chunks", list);
        }
        return tag;
    }

    @Override
    public ChunkFinder fromTag(NbtCompound tag) {
        super.fromTag(tag);
        if (tag.contains("chunks")) {
            chunks.clear();
            for (NbtElement e : tag.getListOrEmpty("chunks")) {
                if (e instanceof NbtLong nl) chunks.add(nl.longValue());
            }
        }
        return this;
    }
}
