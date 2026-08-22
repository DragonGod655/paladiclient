package paladi.client.systems.modules.render;

import paladi.client.events.packets.PacketEvent;
import paladi.client.events.render.Render3DEvent;
import paladi.client.renderer.ShapeMode;
import paladi.client.settings.*;
import paladi.client.renderer.text.TextRenderer;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.render.NametagUtils;
import paladi.client.utils.render.color.Color;
import paladi.client.utils.render.color.SettingColor;
import org.joml.Vector3d;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static paladi.client.PaladiClient.mc;

public class CamouflageChunkFinder extends Module {

    // ===================== SETTINGS =====================
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgRender    = settings.createGroup("Render");

    private final Setting<Integer> entityThreshold = sgDetection.add(new IntSetting.Builder()
        .name("entity-threshold")
        .description("Số BlockEntity tối thiểu để nghi ngờ")
        .defaultValue(4).min(1).max(30).build()
    );

    private final Setting<Integer> hollowThreshold = sgDetection.add(new IntSetting.Builder()
        .name("hollow-threshold")
        .description("Số block rỗng bất thường tối thiểu")
        .defaultValue(20).min(5).max(200).build()
    );

    private final Setting<Boolean> detectCamo     = sgDetection.add(new BoolSetting.Builder()
        .name("detect-camouflage")
        .description("Phát hiện block che đậy (đất đá phủ trên công trình)")
        .defaultValue(true).build()
    );
    private final Setting<Boolean> detectHollow   = sgDetection.add(new BoolSetting.Builder()
        .name("detect-hollow")
        .description("Phát hiện không gian rỗng nhân tạo bên trong địa hình")
        .defaultValue(true).build()
    );
    private final Setting<Boolean> detectEntropy  = sgDetection.add(new BoolSetting.Builder()
        .name("detect-low-entropy")
        .description("Phát hiện vùng có block lặp đi lặp lại bất thường")
        .defaultValue(true).build()
    );

    private final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder()
        .name("fill-color").defaultValue(new SettingColor(255, 140, 0, 40)).build()
    );
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 140, 0, 255)).build()
    );
    private final Setting<SettingColor> highSusColor = sgRender.add(new ColorSetting.Builder()
        .name("high-sus-color").defaultValue(new SettingColor(255, 0, 0, 255)).build()
    );

    // ===================== DATA =====================
    public enum SusReason {
        ENTITY_STASH,   // Nhiều rương/shulker
        HOLLOW_BASE,    // Rỗng bên trong địa hình
        LOW_ENTROPY,    // Block lặp đều bất thường
        CAMOUFLAGE      // Block che đậy công trình
    }

    public static class ChunkSusData {
        public int score;
        public Set<SusReason> reasons = new HashSet<>();
        public ChunkSusData(int score) { this.score = score; }
    }

    private final Map<ChunkPos, ChunkSusData> susChunks = new ConcurrentHashMap<>();

    public CamouflageChunkFinder() {
        super(Categories.BASEFINDING, "camo-chunk-finder",
              "Phát hiện chunk bị chỉnh sửa để che giấu base.");
    }

    @Override
    public void onActivate() { susChunks.clear(); }

    // ===================== SCANNING =====================
    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ChunkDataS2CPacket packet)) return;
        if (mc.world == null) return;

        ChunkPos pos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
        Thread.ofVirtual().start(() -> analyzeChunk(pos));
    }

    private void analyzeChunk(ChunkPos pos) {
        if (mc.world == null) return;
        WorldChunk chunk = mc.world.getChunk(pos.x, pos.z);

        int totalScore = 0;
        ChunkSusData data = new ChunkSusData(0);

        // --- Kiểm tra 1: BlockEntity stash ---
        int entityCount = countSuspiciousEntities(chunk);
        if (entityCount >= entityThreshold.get()) {
            totalScore += entityCount * 2;
            data.reasons.add(SusReason.ENTITY_STASH);
        }

        // --- Kiểm tra 2: Hollow detection ---
        if (detectHollow.get()) {
            int hollowScore = detectHollowSpace(chunk, pos);
            if (hollowScore >= hollowThreshold.get()) {
                totalScore += hollowScore;
                data.reasons.add(SusReason.HOLLOW_BASE);
            }
        }

        // --- Kiểm tra 3: Entropy phân tích ---
        if (detectEntropy.get()) {
            double entropy = calculateBlockEntropy(chunk, pos);
            // Entropy thấp = block đều đặn = nhân tạo
            if (entropy < 1.5) {
                totalScore += (int)((2.0 - entropy) * 20);
                data.reasons.add(SusReason.LOW_ENTROPY);
            }
        }

        // --- Kiểm tra 4: Camouflage layer ---
        if (detectCamo.get()) {
            int camoScore = detectCamouflageLayer(chunk, pos);
            if (camoScore > 10) {
                totalScore += camoScore;
                data.reasons.add(SusReason.CAMOUFLAGE);
            }
        }

        if (totalScore > 0) {
            data.score = totalScore;
            susChunks.put(pos, data);
        } else {
            susChunks.remove(pos);
        }
    }

    // --- Đếm block entity đáng ngờ ---
    private int countSuspiciousEntities(WorldChunk chunk) {
        int count = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity
             || be instanceof BarrelBlockEntity
             || be instanceof ShulkerBoxBlockEntity
             || be instanceof FurnaceBlockEntity
             || be instanceof BlastFurnaceBlockEntity
             || be instanceof SmokerBlockEntity
             || be instanceof MobSpawnerBlockEntity
             || be instanceof BrewingStandBlockEntity) {
                count++;
            }
        }
        return count;
    }

    // --- Phát hiện không gian rỗng ẩn bên trong địa hình ---
    private int detectHollowSpace(WorldChunk chunk, ChunkPos pos) {
        int hollowCount = 0;

        for (int x = 1; x < 15; x++) {
            for (int z = 1; z < 15; z++) {
                for (int y = -60; y < 100; y++) {
                    BlockPos blockPos = new BlockPos(
                        pos.getStartX() + x, y, pos.getStartZ() + z
                    );
                    Block current = chunk.getBlockState(blockPos).getBlock();

                    // Block không khí bao quanh bởi block đặc = không gian rỗng ẩn
                    if (current == Blocks.AIR || current == Blocks.CAVE_AIR) {
                        boolean surrounded = isSurroundedBySolid(chunk, pos, x, y, z);
                        if (surrounded) hollowCount++;
                    }
                }
            }
        }
        return hollowCount;
    }

    private boolean isSurroundedBySolid(WorldChunk chunk, ChunkPos pos, int x, int y, int z) {
        int[][] neighbors = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        int solidCount = 0;

        for (int[] n : neighbors) {
            int nx = x + n[0], ny = y + n[1], nz = z + n[2];
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;

            BlockPos np = new BlockPos(
                pos.getStartX() + nx, ny, pos.getStartZ() + nz
            );
            Block b = chunk.getBlockState(np).getBlock();
            if (b != Blocks.AIR && b != Blocks.CAVE_AIR && b != Blocks.WATER) {
                solidCount++;
            }
        }
        return solidCount >= 5; // 5/6 mặt bị block đặc = rỗng nhân tạo
    }

    // --- Tính entropy Shannon của các loại block ---
    private double calculateBlockEntropy(WorldChunk chunk, ChunkPos pos) {
        Map<Block, Integer> blockCount = new HashMap<>();
        int total = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 50; y < 80; y++) { // Quét tầng có thể xây base
                    BlockPos bp = new BlockPos(
                        pos.getStartX() + x, y, pos.getStartZ() + z
                    );
                    Block b = chunk.getBlockState(bp).getBlock();
                    if (b != Blocks.AIR) {
                        blockCount.merge(b, 1, Integer::sum);
                        total++;
                    }
                }
            }
        }

        if (total == 0) return 3.0; // Chunk rỗng = không đáng ngờ

        // Shannon Entropy: H = -Σ(p * log2(p))
        double entropy = 0.0;
        for (int count : blockCount.values()) {
            double p = (double) count / total;
            if (p > 0) entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    // --- Phát hiện lớp che đậy: block tự nhiên phủ trên công trình ---
    private int detectCamouflageLayer(WorldChunk chunk, ChunkPos pos) {
        int camoScore = 0;

        Set<Block> naturalBlocks = Set.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.DEEPSLATE,
            Blocks.SAND, Blocks.GRAVEL, Blocks.SNOW_BLOCK, Blocks.BEDROCK
        );
        Set<Block> artificialBlocks = Set.of(
            Blocks.COBBLESTONE, Blocks.COBBLED_DEEPSLATE, Blocks.OAK_PLANKS, Blocks.STONE_BRICKS,
            Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.OBSIDIAN,
            Blocks.CRAFTING_TABLE, Blocks.ENCHANTING_TABLE, Blocks.GLASS, Blocks.TORCH
        );

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 0; y++) { // Tập trung vào tầng Deepslate (đá sâu)
                    BlockPos bp = new BlockPos(pos.getStartX() + x, y, pos.getStartZ() + z);
                    Block b = chunk.getBlockState(bp).getBlock();

                    if (artificialBlocks.contains(b)) {
                        camoScore += 2; // Khối nhân tạo ở tầng sâu cực kỳ đáng ngờ
                    }
                }
            }
        }
        return camoScore;
    }

    // ===================== RENDERING =====================
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (susChunks.isEmpty() || mc.player == null) return;

        double playerY = mc.player.getY();
        double boxBottom = Math.max(mc.world.getBottomY(), playerY - 3);
        double boxTop = Math.min(319, playerY + 4);

        for (Map.Entry<ChunkPos, ChunkSusData> entry : susChunks.entrySet()) {
            ChunkPos pos  = entry.getKey();
            ChunkSusData d = entry.getValue();

            double sx = pos.getStartX(), sz = pos.getStartZ();
            double ex = pos.getEndX() + 1, ez = pos.getEndZ() + 1;

            Color lc = d.score > 50 ? highSusColor.get() : lineColor.get();
            Color fc = fillColor.get().copy().a(d.score > 50 ? 60 : 40);

            event.renderer.box(sx, boxBottom, sz, ex, boxTop, ez, fc, lc, ShapeMode.Both, 0);
        }
    }

    @EventHandler
    private void onRender2D(paladi.client.events.render.Render2DEvent event) {
        if (susChunks.isEmpty() || mc.player == null) return;

        double playerY = mc.player.getY();
        TextRenderer text = TextRenderer.get();

        for (Map.Entry<ChunkPos, ChunkSusData> entry : susChunks.entrySet()) {
            ChunkPos pos  = entry.getKey();
            ChunkSusData d = entry.getValue();

            double sx = pos.getStartX(), sz = pos.getStartZ();
            Color lc = d.score > 50 ? highSusColor.get() : lineColor.get();

            Vector3d pos3d = new Vector3d(sx + 8, playerY + 1.5, sz + 8);
            if (NametagUtils.to2D(pos3d, 1.0)) {
                String label = "XYZ: " + (int)(sx + 8) + ", " + (int)playerY + ", " + (int)(sz + 8) + " | Score: " + d.score + " | " + formatReasons(d.reasons);
                NametagUtils.begin(pos3d, event.drawContext);
                text.begin();
                text.render(label, -text.getWidth(label) / 2.0, 0, lc, true);
                text.end();
                NametagUtils.end();
            }
        }
    }

    private String formatReasons(Set<SusReason> reasons) {
        StringBuilder sb = new StringBuilder();
        if (reasons.contains(SusReason.ENTITY_STASH)) sb.append("STASH ");
        if (reasons.contains(SusReason.HOLLOW_BASE))  sb.append("HOLLOW ");
        if (reasons.contains(SusReason.LOW_ENTROPY))  sb.append("PATTERN ");
        if (reasons.contains(SusReason.CAMOUFLAGE))   sb.append("CAMO ");
        return sb.toString().trim();
    }
}
