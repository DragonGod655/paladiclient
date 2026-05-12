package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import paladi.client.events.render.Render2DEvent;
import paladi.client.events.render.Render3DEvent;
import paladi.client.events.world.ChunkDataEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.gui.GuiTheme;
import paladi.client.gui.widgets.WWidget;
import paladi.client.gui.widgets.pressable.WButton;
import paladi.client.renderer.ShapeMode;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.ChatUtils;
import paladi.client.utils.player.PlayerUtils;
import paladi.client.utils.render.NametagUtils;
import paladi.client.utils.render.color.Color;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static paladi.client.PaladiClient.mc;

public class SusChunkFinder extends Module {

    public enum ThreatLevel { 
        LOW(Formatting.DARK_AQUA, "LOW"), 
        MEDIUM(Formatting.YELLOW, "MEDIUM"), 
        HIGH(Formatting.GOLD, "HIGH"), 
        CRITICAL(Formatting.RED, "CRITICAL");

        public final Formatting color;
        public final String label;
        ThreatLevel(Formatting color, String label) { this.color = color; this.label = label; }
    }

    public static class ChunkStats {
        public final int entities;
        public final int tiles;
        public final int baseScore;
        public final ThreatLevel threat;
        public final Color renderColor;

        ChunkStats(int entities, int tiles, int baseScore) {
            this.entities = entities;
            this.tiles = tiles;
            this.baseScore = baseScore;
            
            if (entities > 100 || baseScore >= 80) { threat = ThreatLevel.CRITICAL; renderColor = new Color(255, 32, 96, 100); }
            else if (entities > 50 || tiles > 30 || baseScore >= 50) { threat = ThreatLevel.HIGH; renderColor = new Color(255, 144, 0, 100); }
            else if (entities > 20 || tiles > 15 || baseScore >= 20) { threat = ThreatLevel.MEDIUM; renderColor = new Color(255, 204, 0, 100); }
            else { threat = ThreatLevel.LOW; renderColor = new Color(0, 220, 255, 100); }
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");

    // General
    private final Setting<Integer> simulationDistance = sgGeneral.add(new IntSetting.Builder()
        .name("simulation-distance")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .defaultValue(7)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> alpha = sgGeneral.add(new IntSetting.Builder()
        .name("alpha")
        .defaultValue(58)
        .min(0)
        .sliderMax(255)
        .build()
    );

    private final Setting<Boolean> showHud = sgGeneral.add(new BoolSetting.Builder().name("show-hud").description("Displays the MinecraftHUD style overlay.").defaultValue(true).build());
    private final Setting<Boolean> showRadar = sgGeneral.add(new BoolSetting.Builder().name("show-radar").description("Displays the chunk minimap radar.").defaultValue(true).build());
    private final Setting<Double> radarScale = sgGeneral.add(new DoubleSetting.Builder().name("radar-scale").defaultValue(1.0).min(0.5).sliderMax(2.0).visible(showRadar::get).build());
    private final Setting<Boolean> autoLog = sgGeneral.add(new BoolSetting.Builder().name("auto-log").defaultValue(true).build());

    // Filters
    private final Setting<Boolean> kelp = sgFilters.add(new BoolSetting.Builder().name("kelp").defaultValue(false).build());
    private final Setting<Boolean> caveVines = sgFilters.add(new BoolSetting.Builder().name("cave-vines").defaultValue(false).build());
    private final Setting<Boolean> vines = sgFilters.add(new BoolSetting.Builder().name("vines").defaultValue(false).build());
    private final Setting<Boolean> amethyst = sgFilters.add(new BoolSetting.Builder().name("amethyst").defaultValue(true).build());
    private final Setting<Boolean> bamboo = sgFilters.add(new BoolSetting.Builder().name("bamboo").defaultValue(false).build());
    private final Setting<Boolean> beeNest = sgFilters.add(new BoolSetting.Builder().name("bee-nest").defaultValue(false).build());
    private final Setting<Boolean> rotatedDeepslate = sgFilters.add(new BoolSetting.Builder().name("rotated-deepslate").defaultValue(false).build());

    private final Setting<Boolean> chatFeedbackSetting = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce new suspicious chunks in chat.")
        .defaultValue(true)
        .onChanged(v -> this.chatFeedback = v)
        .build()
    );

    private final Setting<Double> renderHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("render-height")
        .description("The Y level to render the chunk overlay.")
        .defaultValue(80.0)
        .sliderRange(-64, 319)
        .build()
    );

    private final Map<ChunkPos, ChunkStats> chunkCache = new ConcurrentHashMap<>();
    private ChunkPos lastChunk = null;
    private int actionCounter = 0;

    public SusChunkFinder() {
        super(Categories.BASEFINDING, "sus-chunk-finder", "Krypton Ultra Engine - Real SMP Port.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton help = theme.button("Help (Huong Dan)");
        help.action = () -> {
            ChatUtils.sendMsg(Formatting.LIGHT_PURPLE, "--- Sus Chunk Finder Help ---");
            ChatUtils.sendMsg(Formatting.GRAY, "- " + Formatting.RED + "CRITICAL" + Formatting.GRAY + " = Score > 80 or > 100 Entities.");
            ChatUtils.sendMsg(Formatting.GRAY, "- " + Formatting.GOLD + "HIGH" + Formatting.GRAY + " = Score > 50 or > 50 Entities.");
            ChatUtils.sendMsg(Formatting.GRAY, "- " + Formatting.AQUA + "Blue Plane" + Formatting.GRAY + " on radar = Low threat.");
            ChatUtils.sendMsg(Formatting.GRAY, "- Scores are weighted by chest/barrel/shulker counts.");
        };
        return help;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        ChunkPos current = mc.player.getChunkPos();
        ChunkStats stats = chunkCache.get(current);
        
        // Handle chunk crossing
        if (!current.equals(lastChunk)) {
            lastChunk = current;
            // Notify if entering a sus chunk
            if (stats != null && stats.threat.ordinal() >= ThreatLevel.HIGH.ordinal() && chatFeedbackSetting.get()) {
                info(Formatting.RED + "Entering suspicious chunk at " + Formatting.WHITE + current.x + ", " + current.z + "!");
            }
        }
        
        // PERSISTENT ACTION BAR: Refresh every 40 ticks (2 seconds) if stats exist and autoLog is on
        if (stats != null && autoLog.get()) {
            if (actionCounter <= 0) {
                updateActionBar(current, stats);
                actionCounter = 40;
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
        ChunkPos pos = chunk.getPos();
        
        int entities = mc.world.getOtherEntities(null, new net.minecraft.util.math.Box(pos.getStartX(), mc.world.getBottomY(), pos.getStartZ(), pos.getEndX() + 1, 319, pos.getEndZ() + 1)).size();
        int tiles = chunk.getBlockEntities().size();
        int baseScore = calculateBaseScore(chunk);
        
        ChunkStats stats = new ChunkStats(entities, tiles, baseScore);
        
        // Only log if it's new or score changed significantly
        ChunkStats old = chunkCache.get(pos);
        if (old == null || old.baseScore != stats.baseScore) {
            chunkCache.put(pos, stats);
            if (autoLog.get()) logToFile(pos, stats);
            if (chatFeedbackSetting.get() && stats.threat.ordinal() >= ThreatLevel.MEDIUM.ordinal()) {
                info("Found " + stats.threat.color + stats.threat.label + Formatting.GRAY + " chunk at " + Formatting.WHITE + pos.x + ", " + pos.z + Formatting.GRAY + " (Score: " + stats.baseScore + ")");
            }
        }
    }

    private int calculateBaseScore(WorldChunk chunk) {
        int score = 0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = mc.world.getBottomY(); y < 319; y += 4) {
                    Block b = chunk.getBlockState(new BlockPos(x, y, z)).getBlock();
                    
                    if (isInterest(b)) {
                        if (STORAGE.contains(b)) score += 5;
                        if (UTILITY.contains(b)) score += 3;
                        if (amethyst.get() && (b == Blocks.AMETHYST_CLUSTER || b == Blocks.BUDDING_AMETHYST)) score += 2;
                        if (beeNest.get() && (b == Blocks.BEE_NEST || b == Blocks.BEEHIVE)) score += 10;
                        if (kelp.get() && (b == Blocks.KELP || b == Blocks.KELP_PLANT)) score += 1;
                    }
                }
            }
        }
        
        return Math.min((score * sensitivity.get()) / 5, 100);
    }

    private boolean isInterest(Block b) {
        if (STORAGE.contains(b) || UTILITY.contains(b)) return true;
        if (amethyst.get() && (b == Blocks.AMETHYST_CLUSTER || b == Blocks.BUDDING_AMETHYST)) return true;
        if (beeNest.get() && (b == Blocks.BEE_NEST || b == Blocks.BEEHIVE)) return true;
        if (kelp.get() && (b == Blocks.KELP || b == Blocks.KELP_PLANT)) return true;
        if (caveVines.get() && (b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT)) return true;
        if (vines.get() && b == Blocks.VINE) return true;
        if (bamboo.get() && b == Blocks.BAMBOO) return true;
        if (rotatedDeepslate.get() && b == Blocks.DEEPSLATE) return true;
        
        return false;
    }

    private void updateActionBar(ChunkPos pos, ChunkStats stats) {
        net.minecraft.text.MutableText message = net.minecraft.text.Text.empty();
        
        message.append(net.minecraft.text.Text.literal("[KRYPTON] ").formatted(Formatting.DARK_AQUA));
        message.append(net.minecraft.text.Text.literal("Chunk ").formatted(Formatting.GRAY));
        message.append(net.minecraft.text.Text.literal(pos.x + "," + pos.z + " ").formatted(Formatting.AQUA));
        message.append(net.minecraft.text.Text.literal("| ").formatted(Formatting.DARK_AQUA));
        message.append(net.minecraft.text.Text.literal("Score: ").formatted(Formatting.GRAY));
        message.append(net.minecraft.text.Text.literal(stats.baseScore + " ").formatted(Formatting.AQUA));
        message.append(net.minecraft.text.Text.literal("| ").formatted(Formatting.DARK_AQUA));
        message.append(net.minecraft.text.Text.literal(stats.threat.label).formatted(stats.threat.color));

        if (mc.player != null) {
            mc.player.sendMessage(message, true);
        }
    }

    private void logToFile(ChunkPos pos, ChunkStats stats) {
        try (FileWriter fw = new FileWriter("krypton_bases.txt", true)) {
            String logEntry = "Chunk " + pos.x + ", " + pos.z + " | Score: " + stats.baseScore + " | Threat: " + stats.threat.label + " | Entities: " + stats.entities + "\n";
            fw.write(logEntry);
        } catch (IOException ignored) {}
    }

    public ChunkStats getStats(int x, int z) {
        return chunkCache.get(new ChunkPos(x, z));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Map.Entry<ChunkPos, ChunkStats> entry : chunkCache.entrySet()) {
            if (entry.getValue().threat == ThreatLevel.LOW) continue;
            ChunkPos pos = entry.getKey();
            
            if (PlayerUtils.distance(pos.getCenterX(), 64, pos.getCenterZ(), mc.player.getX(), 64, mc.player.getZ()) > simulationDistance.get() * 16) continue;

            Color c = entry.getValue().renderColor.copy().a(alpha.get());
            double renderY = renderHeight.get();
            double x1 = pos.getStartX(), z1 = pos.getStartZ();
            double x2 = pos.getEndX() + 1, z2 = pos.getEndZ() + 1;
            
            event.renderer.box(x1, renderY, z1, x2, renderY + 0.1, z2, c, c, ShapeMode.Both, 0);
        }
    }

    private static final Set<Block> STORAGE = Set.of(
        Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST,
        Blocks.SHULKER_BOX, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX, Blocks.MAGENTA_SHULKER_BOX,
        Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX, Blocks.LIME_SHULKER_BOX, Blocks.PINK_SHULKER_BOX,
        Blocks.GRAY_SHULKER_BOX, Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
        Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX,
        Blocks.BLACK_SHULKER_BOX
    );
    private static final Set<Block> UTILITY = Set.of(Blocks.HOPPER, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.BREWING_STAND, Blocks.CRAFTING_TABLE);
}
