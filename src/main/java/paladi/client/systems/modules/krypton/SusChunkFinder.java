package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import paladi.client.PaladiClient;
import paladi.client.events.render.Render2DEvent;
import paladi.client.events.render.Render3DEvent;
import paladi.client.events.world.BlockUpdateEvent;
import paladi.client.events.world.ChunkDataEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.gui.GuiTheme;
import paladi.client.gui.widgets.WWidget;
import paladi.client.gui.widgets.pressable.WButton;
import paladi.client.renderer.ShapeMode;
import paladi.client.renderer.text.TextRenderer;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.Utils;
import paladi.client.utils.player.ChatUtils;
import paladi.client.utils.player.PlayerUtils;
import paladi.client.utils.render.NametagUtils;
import paladi.client.utils.render.color.Color;
import org.joml.Vector3d;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SusChunkFinder extends Module {
    public enum ThreatLevel {
        LOW("LOW", Formatting.GRAY),
        MEDIUM("MEDIUM", Formatting.YELLOW),
        HIGH("HIGH", Formatting.GOLD),
        CRITICAL("CRITICAL", Formatting.RED);

        public final String label;
        public final Formatting color;

        ThreatLevel(String label, Formatting color) {
            this.label = label;
            this.color = color;
        }
    }

    public static class SusTarget {
        public final BlockPos pos;
        public final String name;
        public final Color color;

        public SusTarget(BlockPos pos, String name, Color color) {
            this.pos = pos;
            this.name = name;
            this.color = color;
        }
    }

    private static class ScanResult {
        final int score;
        final boolean detected;
        final List<SusTarget> targets;

        ScanResult(int score, boolean detected, List<SusTarget> targets) {
            this.score = score;
            this.detected = detected;
            this.targets = targets;
        }
    }

    public static class ChunkStats {
        public final int entities;
        public final int tiles;
        public final int baseScore;
        public final ThreatLevel threat;
        public final Color renderColor;
        public final List<SusTarget> targets;

        ChunkStats(int entities, int tiles, int baseScore, boolean detected, List<SusTarget> targets) {
            this.entities = entities;
            this.tiles = tiles;
            this.baseScore = baseScore;
            this.targets = targets;

            if (detected || baseScore > 0) {
                threat = ThreatLevel.CRITICAL;
                renderColor = new Color(255, 20, 20, 255); // ĐỎ RỰC KRYPTON
            } else {
                threat = ThreatLevel.LOW;
                renderColor = null;
            }
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Detectors & Growth Filters");

    // General
    private final Setting<Integer> simulationDistance = sgGeneral.add(new IntSetting.Builder()
        .name("simulation-distance")
        .description("Khoảng cách quét và hiển thị chunk (tối đa 64 chunks).")
        .defaultValue(32)
        .min(1)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<Integer> alpha = sgGeneral.add(new IntSetting.Builder()
        .name("alpha")
        .description("Độ đậm của mặt phẳng đỏ.")
        .defaultValue(90)
        .min(0)
        .sliderMax(255)
        .build()
    );

    private final Setting<Boolean> highlightCurrent = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-current")
        .description("Tô màu chunk hiện tại nếu phát hiện nghi vấn.")
        .defaultValue(true)
        .build()
    );


    private final Setting<Boolean> autoLog = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-log")
        .description("Tự động lưu tọa độ vào file logs bases.txt.")
        .defaultValue(true)
        .build()
    );

    // Filters / Detectors List
    private final Setting<Boolean> storage = sgFilters.add(new BoolSetting.Builder()
        .name("storage-containers")
        .description("Quét Rương, Shulker, Barrel, Ender Chest, Spawner.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> amethyst = sgFilters.add(new BoolSetting.Builder()
        .name("amethyst-growth")
        .description("Quét mầm Thạch anh phát triển (Amethyst Clusters & Buds mọc dài).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> kelp = sgFilters.add(new BoolSetting.Builder()
        .name("kelp-growth")
        .description("Quét Tảo bẹ (Kelp) mọc cao do người chơi nạp chunk.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> caveVines = sgFilters.add(new BoolSetting.Builder()
        .name("cave-vines-growth")
        .description("Quét Dây leo phát quang (Cave Vines & Glow Berries) trong hang.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> netherVines = sgFilters.add(new BoolSetting.Builder()
        .name("nether-vines-growth")
        .description("Quét Dây xoắn & Dây rủ (Twisting & Weeping Vines) ở Nether.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> bamboo = sgFilters.add(new BoolSetting.Builder()
        .name("bamboo-growth")
        .description("Quét Tre (Bamboo) mọc cao do người chơi nạp chunk.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sugarCane = sgFilters.add(new BoolSetting.Builder()
        .name("sugar-cane-cactus")
        .description("Quét Mía & Xương rồng mọc cao.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> deepslateOnly = sgFilters.add(new BoolSetting.Builder()
        .name("deepslate-only")
        .description("Chỉ quét ở tầng Deepslate (Y <= 0).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxY = sgFilters.add(new IntSetting.Builder()
        .name("max-y")
        .description("Độ cao Y tối đa để quét (khi bật deepslate-only).")
        .defaultValue(0)
        .min(-64)
        .max(319)
        .sliderRange(-64, 100)
        .visible(deepslateOnly::get)
        .build()
    );

    private final Map<ChunkPos, ChunkStats> chunkCache = new ConcurrentHashMap<>();
    private final java.util.concurrent.ExecutorService scannerThread = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Vector3d worldPos = new Vector3d();
    private ChunkPos lastChunk = null;
    private int actionCounter = 0;

    public SusChunkFinder() {
        super(Categories.BASEFINDING, "sus-chunk-finder", "Krypton Ultra Engine - Real SMP Port.");
    }

    @Override
    public void onActivate() {
        chunkCache.clear();
        lastChunk = null;
        rescanLoadedChunks();
    }

    @Override
    public void onDeactivate() {
        chunkCache.clear();
        lastChunk = null;
    }

    private void rescanLoadedChunks() {
        if (mc.world == null) return;
        scannerThread.submit(() -> {
            if (!isActive() || mc.world == null) return;
            for (net.minecraft.world.chunk.Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk wc) {
                    processChunk(wc);
                }
            }
        });
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton help = theme.button("Help (Huong Dan)");
        help.action = () -> {
            ChatUtils.sendMsg(Formatting.LIGHT_PURPLE, "--- Sus Chunk Finder (Krypton Edition) ---");
            ChatUtils.sendMsg(Formatting.GRAY, "- Bật/tắt các loại thực vật & mầm phát triển (Amethyst, Kelp, Cave Vines, v.v.) trong mục Filters.");
            ChatUtils.sendMsg(Formatting.GRAY, "- Chunk có dấu hiệu người chơi nạp/phát triển sẽ được tô " + Formatting.RED + "MẶT PHẲNG ĐỎ PHẲNG LÌ TRÊN MẶT ĐẤT" + Formatting.GRAY + ".");
        };
        return help;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        ChunkPos current = mc.player.getChunkPos();

        ChunkStats stats = chunkCache.get(current);

        if (!current.equals(lastChunk)) {
            lastChunk = current;
        }

        if (autoLog.get()) {
            if (actionCounter <= 0) {
                updateActionBar(current, stats);
                actionCounter = 10;
            } else {
                actionCounter--;
            }
        } else {
            actionCounter = 0;
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        WorldChunk chunk = event.chunk();
        if (chunk == null) return;
        scannerThread.submit(() -> {
            if (isActive()) processChunk(chunk);
        });
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null) return;
        WorldChunk wc = mc.world.getWorldChunk(event.pos);
        if (wc != null) {
            scannerThread.submit(() -> {
                if (isActive()) processChunk(wc);
            });
        }
    }

    private ChunkStats processChunk(WorldChunk chunk) {
        if (chunk == null || mc.world == null) return null;
        try {
            ChunkPos pos = chunk.getPos();

            int scanMinY = mc.world.getBottomY();
            int baseMaxY = deepslateOnly.get() ? maxY.get() : 319;

            int entities = mc.world.getOtherEntities(null, new net.minecraft.util.math.Box(pos.getStartX(), scanMinY, pos.getStartZ(), pos.getEndX() + 1, baseMaxY, pos.getEndZ() + 1)).size();
            int tiles = 0;
            for (BlockPos bePos : chunk.getBlockEntityPositions()) {
                if (bePos.getY() <= baseMaxY && bePos.getY() >= scanMinY) {
                    tiles++;
                }
            }

            ScanResult res = calculateBaseScore(chunk, baseMaxY);
            ChunkStats stats = new ChunkStats(entities, tiles, res.score, res.detected, res.targets);

            if (chunkCache.size() > 2500) {
                chunkCache.entrySet().removeIf(e -> e.getValue().threat == ThreatLevel.LOW);
                if (chunkCache.size() > 2500) {
                    chunkCache.clear();
                }
            }

            ChunkStats old = chunkCache.put(pos, stats);
            if (old == null || old.baseScore != stats.baseScore) {
                if (autoLog.get() && stats.threat != ThreatLevel.LOW) logToFile(pos, stats);
            }
            return stats;
        } catch (Exception e) {
            return null;
        }
    }

    private ScanResult calculateBaseScore(WorldChunk chunk, int baseMaxY) {
        int score = 0;
        boolean detected = false;
        List<SusTarget> targets = new ArrayList<>();

        // 1. Storage & Containers (Rương, Shulker, Barrel, Spawner, v.v.)
        if (storage.get()) {
            for (BlockPos bp : chunk.getBlockEntityPositions()) {
                if (bp.getY() <= baseMaxY) {
                    detected = true;
                    score += 35;
                }
            }
        }

        // 2. Quét nhanh theo từng ChunkSection để phát hiện các thực vật / mầm phát triển
        ChunkSection[] sections = chunk.getSectionArray();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        for (int s = 0; s < sections.length; s++) {
            ChunkSection section = sections[s];
            if (section == null || section.isEmpty()) continue;
            int sectionMinY = chunk.sectionIndexToCoord(s) << 4;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block b = state.getBlock();
                        if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.WATER) continue;

                        int worldY = sectionMinY + y;
                        if (deepslateOnly.get() && worldY > baseMaxY) continue;

                        BlockPos pos = new BlockPos(startX + x, worldY, startZ + z);

                        // A. Thạch anh phát triển (Amethyst Growth)
                        if (amethyst.get() && isAmethystBlock(b)) {
                            detected = true;
                            if (b == Blocks.AMETHYST_CLUSTER || b == Blocks.LARGE_AMETHYST_BUD) {
                                score += 35;
                            } else {
                                score += 15;
                            }
                            if (targets.size() < 64) {
                                targets.add(new SusTarget(pos, "Amethyst", new Color(190, 40, 255, 255)));
                            }
                        }

                        // B. Tảo bẹ phát triển (Kelp Growth)
                        if (kelp.get() && (b == Blocks.KELP || b == Blocks.KELP_PLANT)) {
                            detected = true;
                            score += 20;
                        }

                        // C. Dây leo phát quang (Cave Vines)
                        if (caveVines.get() && (b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT)) {
                            detected = true;
                            score += 20;
                        }

                        // D. Dây leo Nether (Twisting / Weeping Vines)
                        if (netherVines.get() && (b == Blocks.TWISTING_VINES || b == Blocks.TWISTING_VINES_PLANT || b == Blocks.WEEPING_VINES || b == Blocks.WEEPING_VINES_PLANT)) {
                            detected = true;
                            score += 20;
                        }

                        // E. Tre (Bamboo)
                        if (bamboo.get() && (b == Blocks.BAMBOO || b == Blocks.BAMBOO_SAPLING)) {
                            detected = true;
                            score += 20;
                        }

                        // F. Mía & Xương rồng (Sugar Cane & Cactus)
                        if (sugarCane.get() && (b == Blocks.SUGAR_CANE || b == Blocks.CACTUS)) {
                            detected = true;
                            score += 20;
                        }
                    }
                }
            }
        }

        int finalScore = Math.min(score, 100);
        return new ScanResult(finalScore, detected, targets);
    }

    private boolean isAmethystBlock(Block b) {
        return b == Blocks.AMETHYST_CLUSTER || b == Blocks.BUDDING_AMETHYST || b == Blocks.AMETHYST_BLOCK 
            || b == Blocks.SMALL_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.LARGE_AMETHYST_BUD;
    }

    private void updateActionBar(ChunkPos pos, ChunkStats stats) {
        MutableText message = Text.empty();

        int posX = mc.player != null ? mc.player.getBlockX() : pos.getStartX();
        int posY = mc.player != null ? mc.player.getBlockY() : 64;
        int posZ = mc.player != null ? mc.player.getBlockZ() : pos.getStartZ();

        message.append(Text.literal("[KRYPTON] ").formatted(Formatting.DARK_RED));
        message.append(Text.literal("XYZ ").formatted(Formatting.GRAY));
        message.append(Text.literal(posX + ", " + posY + ", " + posZ + " ").formatted(Formatting.RED));
        message.append(Text.literal("| ").formatted(Formatting.DARK_GRAY));
        message.append(Text.literal("Score: ").formatted(Formatting.GRAY));
        if (stats != null && stats.threat != ThreatLevel.LOW) {
            message.append(Text.literal(stats.baseScore + "% ").formatted(Formatting.RED));
            message.append(Text.literal("| ").formatted(Formatting.DARK_GRAY));
            message.append(Text.literal(stats.threat.label).formatted(Formatting.RED));
        } else {
            message.append(Text.literal("0% ").formatted(Formatting.GRAY));
            message.append(Text.literal("| ").formatted(Formatting.DARK_GRAY));
            message.append(Text.literal("LOW").formatted(Formatting.DARK_GRAY));
        }

        if (mc.player != null) {
            mc.player.sendMessage(message, true);
        }
    }

    private void logToFile(ChunkPos pos, ChunkStats stats) {
        paladi.client.utils.network.PaladiExecutor.execute(() -> {
            try {
                File dir = new File(PaladiClient.FOLDER, "krypton");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, "bases.txt");
                FileWriter fw = new FileWriter(file, true);
                int posX = pos.getStartX();
                int posY = mc.player != null ? (int) mc.player.getY() : 64;
                int posZ = pos.getStartZ();
                String logEntry = "XYZ: " + posX + ", " + posY + ", " + posZ + " | Score: " + stats.baseScore + "% | Threat: " + stats.threat.label + " | Entities: " + stats.entities + "\n";
                fw.write(logEntry);
                fw.close();
            } catch (IOException ignored) {}
        });
    }

    public ChunkStats getStats(int x, int z) {
        return chunkCache.get(new ChunkPos(x, z));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        double playerY = mc.player.getY();

        for (Map.Entry<ChunkPos, ChunkStats> entry : chunkCache.entrySet()) {
            ChunkPos pos = entry.getKey();
            ChunkStats stats = entry.getValue();

            if (stats == null || stats.renderColor == null || stats.threat == ThreatLevel.LOW) continue;

            if (PlayerUtils.distance(pos.getCenterX(), playerY, pos.getCenterZ(), mc.player.getX(), playerY, mc.player.getZ()) > simulationDistance.get() * 16) continue;

            renderChunkBlocks(event, pos, stats, playerY);
        }
    }

    private void renderChunkBlocks(Render3DEvent event, ChunkPos pos, ChunkStats stats, double playerY) {
        int startX = pos.getStartX();
        int startZ = pos.getStartZ();
        int endX = pos.getEndX() + 1;
        int endZ = pos.getEndZ() + 1;

        Color redFill = new Color(255, 30, 30, Math.max(alpha.get(), 85));
        Color redLine = new Color(255, 20, 20, 255);

        int ySurface = mc.world != null ? mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getCenterX(), pos.getCenterZ()) : (int) playerY;
        double yRender = (mc.world != null && mc.world.getDimension().hasCeiling()) ? Math.floor(playerY) : ySurface;
        if (Math.abs(playerY - ySurface) > 25) {
            yRender = Math.floor(playerY);
        }

        // 1. TẤM MẶT PHẲNG ĐỎ PHẲNG LÌ BÁM TRÊN MẶT ĐẤT (FLAT HORIZONTAL QUAD & BORDER)
        double yPlane = yRender + 0.05;
        event.renderer.quadHorizontal(startX, yPlane, startZ, endX, endZ, redFill);
        event.renderer.line(startX, yPlane, startZ, endX, yPlane, startZ, redLine);
        event.renderer.line(endX, yPlane, startZ, endX, yPlane, endZ, redLine);
        event.renderer.line(endX, yPlane, endZ, startX, yPlane, endZ, redLine);
        event.renderer.line(startX, yPlane, endZ, startX, yPlane, startZ, redLine);

        if (yRender != ySurface && mc.world != null && !mc.world.getDimension().hasCeiling()) {
            double ySurfacePlane = ySurface + 0.05;
            event.renderer.quadHorizontal(startX, ySurfacePlane, startZ, endX, endZ, redFill);
            event.renderer.line(startX, ySurfacePlane, startZ, endX, ySurfacePlane, startZ, redLine);
            event.renderer.line(endX, ySurfacePlane, startZ, endX, ySurfacePlane, endZ, redLine);
            event.renderer.line(endX, ySurfacePlane, endZ, startX, ySurfacePlane, endZ, redLine);
            event.renderer.line(startX, ySurfacePlane, endZ, startX, ySurfacePlane, startZ, redLine);
        }

        // 2. CÁC ĐIỂM THẠCH ANH (Chỉ vẽ Quả Cầu Tím Tròn 3D)
        if (stats.targets != null && !stats.targets.isEmpty()) {
            for (SusTarget target : stats.targets) {
                if ("Amethyst".equals(target.name)) {
                    double cx = target.pos.getX() + 0.5;
                    double cy = target.pos.getY() + 0.5;
                    double cz = target.pos.getZ() + 0.5;
                    renderPurpleSphere(event, cx, cy, cz, 0.45);
                }
            }
        }
    }

    private void renderPurpleSphere(Render3DEvent event, double cx, double cy, double cz, double radius) {
        Color coreColor = new Color(220, 50, 255, 220);
        Color glowColor = new Color(245, 120, 255, 255);
        Color haloColor = new Color(180, 30, 255, 60);

        int segments = 16;
        double step = (Math.PI * 2) / segments;

        for (int i = 0; i < segments; i++) {
            double a1 = i * step;
            double a2 = (i + 1) * step;
            double x1 = cx + Math.cos(a1) * radius;
            double z1 = cz + Math.sin(a1) * radius;
            double x2 = cx + Math.cos(a2) * radius;
            double z2 = cz + Math.sin(a2) * radius;
            event.renderer.line(x1, cy, z1, x2, cy, z2, glowColor);
        }

        for (int i = 0; i < segments; i++) {
            double a1 = i * step;
            double a2 = (i + 1) * step;
            double x1 = cx + Math.cos(a1) * radius;
            double y1 = cy + Math.sin(a1) * radius;
            double x2 = cx + Math.cos(a2) * radius;
            double y2 = cy + Math.sin(a2) * radius;
            event.renderer.line(x1, y1, cz, x2, y2, cz, glowColor);
        }

        for (int i = 0; i < segments; i++) {
            double a1 = i * step;
            double a2 = (i + 1) * step;
            double y1 = cy + Math.sin(a1) * radius;
            double z1 = cz + Math.cos(a1) * radius;
            double y2 = cy + Math.sin(a2) * radius;
            double z2 = cz + Math.cos(a2) * radius;
            event.renderer.line(cx, y1, z1, cx, y2, z2, glowColor);
        }

        double rInner = radius * 0.6;
        event.renderer.box(cx - rInner, cy - rInner, cz - rInner, cx + rInner, cy + rInner, cz + rInner, coreColor, glowColor, ShapeMode.Both, 0);
        event.renderer.box(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius, haloColor, glowColor, ShapeMode.Lines, 0);
    }
}
