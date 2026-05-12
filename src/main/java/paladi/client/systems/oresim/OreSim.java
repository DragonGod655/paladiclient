package paladi.client.systems.oresim;

import paladi.client.events.render.Render3DEvent;
import paladi.client.events.world.BlockUpdateEvent;
import paladi.client.events.world.ChunkDataEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import paladi.client.utils.Utils;
import paladi.client.utils.player.PlayerUtils;
import paladi.client.utils.world.Dimension;
import paladi.client.gui.widgets.pressable.WButton;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OreSim extends Module {

    private final Map<Long, Map<Ore, Set<Vec3d>>> chunkRenderers = new ConcurrentHashMap<>();
    private Map<RegistryKey<Biome>, List<Ore>> oreConfig;
    
    public enum AirCheck {
        ON_LOAD,
        RECHECK,
        OFF
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
            .name("chunk-range")
            .description("Distance of chunks being shown.")
            .defaultValue(5)
            .min(1)
            .sliderMax(10)
            .build()
    );

    private final Setting<AirCheck> airCheck = sgGeneral.add(new EnumSetting.Builder<AirCheck>()
            .name("air-check-mode")
            .description("Checks if there is air at a calculated ore pos.")
            .defaultValue(AirCheck.RECHECK)
            .build()
    );

    private final Setting<Boolean> debugLogs = sgGeneral.add(new BoolSetting.Builder()
            .name("debug-logs")
            .description("Show calculation status in chat.")
            .defaultValue(true)
            .build()
    );

    public OreSim() {
        super(Categories.World, "ore-sim", "Authentic Rejects Xray Simulation.");
        SettingGroup sgOres = settings.createGroup("Ores");
        Ore.oreSettings.forEach(sgOres::add);
        
        /*sgGeneral.add(new WButton.Builder()
            .name("Reload Simulation")
            .action(() -> {
                if (isActive()) reload();
            })
            .build()
        );*/
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || oreConfig == null) return;

        if (Seeds.get().getSeed() != null) {
            int chunkX = mc.player.getChunkPos().x;
            int chunkZ = mc.player.getChunkPos().z;
            int rangeVal = horizontalRadius.get();

            for (int range = 0; range <= rangeVal; range++) {
                for (int x = -range + chunkX; x <= range + chunkX; x++) {
                    renderChunk(x, chunkZ + range - rangeVal, event);
                }
                for (int x = (-range) + 1 + chunkX; x < range + chunkX; x++) {
                    renderChunk(x, chunkZ - range + rangeVal + 1, event);
                }
            }
        }
    }

    private void renderChunk(int x, int z, Render3DEvent event) {
        long chunkKey = ChunkPos.toLong(x, z);
        if (chunkRenderers.containsKey(chunkKey)) {
            Map<Ore, Set<Vec3d>> chunk = chunkRenderers.get(chunkKey);
            for (Map.Entry<Ore, Set<Vec3d>> oreRenders : chunk.entrySet()) {
                if (oreRenders.getKey().active.get()) {
                    for (Vec3d pos : oreRenders.getValue()) {
                        event.renderer.boxLines(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1, oreRenders.getKey().color, 0);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (airCheck.get() != AirCheck.RECHECK || event.newState.isAir()) return;
        long chunkKey = ChunkPos.toLong(event.pos);
        if (chunkRenderers.containsKey(chunkKey)) {
            Vec3d pos = Vec3d.of(event.pos);
            for (var ore : chunkRenderers.get(chunkKey).values()) {
                ore.remove(pos);
            }
        }
    }

    @Override
    public void onActivate() {
        if (Seeds.get().getSeed() == null) {
            error("No seed found. To set a seed do .seed <seed>");
            this.toggle();
            return;
        }
        reload();
    }

    @Override
    public void onDeactivate() {
        this.chunkRenderers.clear();
        this.oreConfig = null;
    }

    @EventHandler
    private void onSeedChanged(SeedChangedEvent event) {
        if (debugLogs.get()) info(Formatting.YELLOW + "Seed changed, reloading simulation...");
        reload();
    }

    private void reload() {
        if (Seeds.get().getSeed() == null) return;
        oreConfig = Ore.getRegistry(PlayerUtils.getDimension());
        chunkRenderers.clear();
        if (mc.world != null) {
            if (debugLogs.get()) info(Formatting.AQUA + "Calculating ores for visible chunks...");
            for (var chunk : Utils.chunks(false)) {
                try {
                    doMathOnChunk(chunk);
                } catch (Exception e) {
                    paladi.client.systems.modules.Modules.get().get(paladi.client.systems.modules.krypton.ChibiDebugger.class).reportError("OreSim-Reload", e);
                }
            }
        }
    }

    @EventHandler
    public void onChunkData(ChunkDataEvent event) {
        try {
            doMathOnChunk(event.chunk());
        } catch (Exception e) {
            paladi.client.systems.modules.Modules.get().get(paladi.client.systems.modules.krypton.ChibiDebugger.class).reportError("OreSim", e);
        }
    }

    private void doMathOnChunk(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkPos.toLong();
        if (chunkRenderers.containsKey(chunkKey) || mc.world == null) return;
        
        Seed currentSeed = Seeds.get().getSeed();
        if (currentSeed == null) return;

        Set<RegistryKey<Biome>> biomes = new HashSet<>();
        for (int by = mc.world.getBottomY() >> 2; by < (mc.world.getBottomY() + mc.world.getHeight()) >> 2; by++) {
            for (int bx = 0; bx < 4; bx++) {
                for (int bz = 0; bz < 4; bz++) {
                    biomes.add(chunk.getBiomeForNoiseGen(bx, by, bz).getKey().get());
                }
            }
        }

        Set<Ore> oreSet = biomes.stream().flatMap(b -> getDefaultOres(b).stream()).collect(Collectors.toSet());
        int chunkX = chunkPos.x << 4;
        int chunkZ = chunkPos.z << 4;
        
        ChunkRandom random = new ChunkRandom(net.minecraft.util.math.random.Random.create(0));
        long populationSeed = random.setPopulationSeed(currentSeed.seed, chunkX, chunkZ);
        HashMap<Ore, Set<Vec3d>> h = new HashMap<>();

        for (Ore ore : oreSet) {
            HashSet<Vec3d> ores = new HashSet<>();
            random.setDecoratorSeed(populationSeed, ore.index, ore.step);
            int repeat = ore.count.get(random);

            for (int i = 0; i < repeat; i++) {
                if (ore.rarity != 1F && random.nextFloat() >= 1/ore.rarity) continue;
                int x = random.nextInt(16) + chunkX;
                int z = random.nextInt(16) + chunkZ;
                int y = ore.heightProvider.get(random, ore.heightContext);
                BlockPos origin = new BlockPos(x, y, z);
                
                // Fixed: Noise Biome coordinates
                RegistryKey<Biome> biome = chunk.getBiomeForNoiseGen(x >> 2, y >> 2, z >> 2).getKey().get();

                if (!getDefaultOres(biome).contains(ore)) continue;
                if (ore.scattered) {
                    ores.addAll(generateHidden(random, origin, ore.size));
                } else {
                    ores.addAll(generateNormal(random, origin, ore.size, ore.discardOnAirChance));
                }
            }
            if (!ores.isEmpty()) h.put(ore, ores);
        }
        chunkRenderers.put(chunkKey, h);
    }

    private List<Ore> getDefaultOres(RegistryKey<Biome> biomeKey) {
        if (oreConfig == null) return Collections.emptyList();
        if (oreConfig.containsKey(biomeKey)) return oreConfig.get(biomeKey);
        return oreConfig.values().stream().findAny().orElse(Collections.emptyList());
    }

    private ArrayList<Vec3d> generateNormal(ChunkRandom random, BlockPos blockPos, int veinSize, float discardOnAir) {
        float f = random.nextFloat() * 3.1415927F;
        float g = (float) veinSize / 8.0F;
        int i = MathHelper.ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = (double) blockPos.getX() + Math.sin(f) * (double) g;
        double e = (double) blockPos.getX() - Math.sin(f) * (double) g;
        double h = (double) blockPos.getZ() + Math.cos(f) * (double) g;
        double j = (double) blockPos.getZ() - Math.cos(f) * (double) g;
        double l = (blockPos.getY() + random.nextInt(3) - 2);
        double m = (blockPos.getY() + random.nextInt(3) - 2);
        int n = blockPos.getX() - MathHelper.ceil(g) - i;
        int o = blockPos.getY() - 2 - i;
        int p = blockPos.getZ() - MathHelper.ceil(g) - i;
        int q = 2 * (MathHelper.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; ++s) {
            for (int t = p; t <= p + q; ++t) {
                if (o <= mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, s, t)) {
                    return this.generateVeinPart(random, veinSize, d, e, h, j, l, m, n, o, p, q, r, discardOnAir);
                }
            }
        }
        return new ArrayList<>();
    }

    private ArrayList<Vec3d> generateVeinPart(ChunkRandom random, int veinSize, double startX, double endX, double startZ, double endZ, double startY, double endY, int x, int y, int z, int size, int i, float discardOnAir) {
        BitSet bitSet = new BitSet(size * i * size);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double[] ds = new double[veinSize * 4];
        ArrayList<Vec3d> poses = new ArrayList<>();

        for (int n = 0; n < veinSize; ++n) {
            float f = (float) n / (float) veinSize;
            double p = MathHelper.lerp(f, startX, endX);
            double q = MathHelper.lerp(f, startY, endY);
            double r = MathHelper.lerp(f, startZ, endZ);
            double s = random.nextDouble() * (double) veinSize / 16.0D;
            double m = ((double) (MathHelper.sin(3.1415927F * f) + 1.0F) * s + 1.0D) / 2.0D;
            ds[n * 4] = p; ds[n * 4 + 1] = q; ds[n * 4 + 2] = r; ds[n * 4 + 3] = m;
        }

        for (int n = 0; n < veinSize - 1; ++n) {
            if (!(ds[n * 4 + 3] <= 0.0D)) {
                for (int o = n + 1; o < veinSize; ++o) {
                    if (!(ds[o * 4 + 3] <= 0.0D)) {
                        double p = ds[n * 4] - ds[o * 4];
                        double q = ds[n * 4 + 1] - ds[o * 4 + 1];
                        double r = ds[n * 4 + 2] - ds[o * 4 + 2];
                        double s = ds[n * 4 + 3] - ds[o * 4 + 3];
                        if (s * s > p * p + q * q + r * r) {
                            if (s > 0.0D) ds[o * 4 + 3] = -1.0D;
                            else ds[n * 4 + 3] = -1.0D;
                        }
                    }
                }
            }
        }

        for (int n = 0; n < veinSize; ++n) {
            double u = ds[n * 4 + 3];
            if (!(u < 0.0D)) {
                double v = ds[n * 4]; double w = ds[n * 4 + 1]; double aa = ds[n * 4 + 2];
                int ab = Math.max(MathHelper.floor(v - u), x); int ac = Math.max(MathHelper.floor(w - u), y); int ad = Math.max(MathHelper.floor(aa - u), z);
                int ae = Math.max(MathHelper.floor(v + u), ab); int af = Math.max(MathHelper.floor(w + u), ac); int ag = Math.max(MathHelper.floor(aa + u), ad);

                for (int ah = ab; ah <= ae; ++ah) {
                    double ai = ((double) ah + 0.5D - v) / u;
                    if (ai * ai < 1.0D) {
                        for (int aj = ac; aj <= af; ++aj) {
                            double ak = ((double) aj + 0.5D - w) / u;
                            if (ai * ai + ak * ak < 1.0D) {
                                for (int al = ad; al <= ag; ++al) {
                                    double am = ((double) al + 0.5D - aa) / u;
                                    if (ai * ai + ak * ak + am * am < 1.0D) {
                                        int an = ah - x + (aj - y) * size + (al - z) * size * i;
                                        if (!bitSet.get(an)) {
                                            bitSet.set(an); mutable.set(ah, aj, al);
                                            if (aj >= mc.world.getBottomY() && aj < (mc.world.getBottomY() + mc.world.getHeight()) && (airCheck.get() == AirCheck.OFF || mc.world.getBlockState(mutable).isAir() == false)) {
                                                if (shouldPlace(mutable, discardOnAir, random)) {
                                                    poses.add(new Vec3d(ah, aj, al));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return poses;
    }

    private boolean shouldPlace(BlockPos orePos, float discardOnAir, ChunkRandom random) {
        if (discardOnAir == 0F || (discardOnAir != 1F && random.nextFloat() >= discardOnAir)) return true;
        for (Direction direction : Direction.values()) {
            if (mc.world.getBlockState(orePos.offset(direction)).isAir() && discardOnAir != 1F) return false;
        }
        return true;
    }

    private ArrayList<Vec3d> generateHidden(ChunkRandom random, BlockPos blockPos, int size) {
        ArrayList<Vec3d> poses = new ArrayList<>();
        int i = random.nextInt(size + 1);
        for (int j = 0; j < i; ++j) {
            int s = Math.min(j, 7);
            int x = Math.round((random.nextFloat() - random.nextFloat()) * (float) s) + blockPos.getX();
            int y = Math.round((random.nextFloat() - random.nextFloat()) * (float) s) + blockPos.getY();
            int z = Math.round((random.nextFloat() - random.nextFloat()) * (float) s) + blockPos.getZ();
            if (airCheck.get() == AirCheck.OFF || mc.world.getBlockState(new BlockPos(x, y, z)).isAir() == false) {
                poses.add(new Vec3d(x, y, z));
            }
        }
        return poses;
    }
}
