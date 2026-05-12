package paladi.client.systems.oresim;

import paladi.client.settings.BoolSetting;
import paladi.client.settings.Setting;
import paladi.client.utils.render.color.Color;
import paladi.client.utils.world.Dimension;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.Registries;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.OreFeature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.util.*;

import static paladi.client.PaladiClient.mc;

public class Ore {

    private static final Setting<Boolean> coal        = new BoolSetting.Builder().name("Coal").defaultValue(true).build();
    private static final Setting<Boolean> iron        = new BoolSetting.Builder().name("Iron").defaultValue(true).build();
    private static final Setting<Boolean> gold        = new BoolSetting.Builder().name("Gold").defaultValue(true).build();
    private static final Setting<Boolean> redstone    = new BoolSetting.Builder().name("Redstone").defaultValue(true).build();
    private static final Setting<Boolean> diamond     = new BoolSetting.Builder().name("Diamond").defaultValue(true).build();
    private static final Setting<Boolean> lapis       = new BoolSetting.Builder().name("Lapis").defaultValue(true).build();
    private static final Setting<Boolean> copper      = new BoolSetting.Builder().name("Copper").defaultValue(true).build();
    private static final Setting<Boolean> emerald     = new BoolSetting.Builder().name("Emerald").defaultValue(true).build();
    private static final Setting<Boolean> quartz      = new BoolSetting.Builder().name("Quartz").defaultValue(true).build();
    private static final Setting<Boolean> debris      = new BoolSetting.Builder().name("Ancient Debris").defaultValue(true).build();
    public static final  List<Setting<Boolean>>   oreSettings = new ArrayList<>(Arrays.asList(coal, iron, gold, redstone, diamond, lapis, copper, emerald, quartz, debris));

    public static Map<RegistryKey<Biome>, List<Ore>> getRegistry(Dimension dimension) {
        if (mc.world == null) return Collections.emptyMap();
        
        RegistryWrapper.WrapperLookup registry = mc.world.getRegistryManager();
        RegistryWrapper.Impl<PlacedFeature> features = registry.getOrThrow(RegistryKeys.PLACED_FEATURE);
        
        // Use Overworld biomes as a base if possible
        var biomes = registry.getOrThrow(RegistryKeys.BIOME).streamEntries().map(RegistryEntry.Reference::value).toList();
        
        // FeatureSorter is internal/removed in some versions, using manual mapping if needed
        // For simplicity, let's assume index is handled by the feature itself or omit if not needed
        
        Map<PlacedFeature, Ore> featureToOre = new HashMap<>();
        // Note: OrePlacements -> OrePlacedFeatures in Yarn
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_coal_lower")), 6, coal, new Color(47, 44, 54));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_coal_upper")), 6, coal, new Color(47, 44, 54));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_iron_middle")), 6, iron, new Color(236, 173, 119));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_iron_small")), 6, iron, new Color(236, 173, 119));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_iron_upper")), 6, iron, new Color(236, 173, 119));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_gold")), 6, gold, new Color(247, 229, 30));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_gold_lower")), 6, gold, new Color(247, 229, 30));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_gold_extra")), 6, gold, new Color(247, 229, 30));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_gold_nether")), 7, gold, new Color(247, 229, 30));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_gold_deltas")), 7, gold, new Color(247, 229, 30));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_redstone")), 6, redstone, new Color(245, 7, 23));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_redstone_lower")), 6, redstone, new Color(245, 7, 23));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_diamond")), 6, diamond, new Color(33, 244, 255));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_diamond_buried")), 6, diamond, new Color(33, 244, 255));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_diamond_large")), 6, diamond, new Color(33, 244, 255));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_diamond_medium")), 6, diamond, new Color(33, 244, 255));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_lapis")), 6, lapis, new Color(8, 26, 189));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_lapis_buried")), 6, lapis, new Color(8, 26, 189));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_copper")), 6, copper, new Color(239, 151, 0));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_copper_large")), 6, copper, new Color(239, 151, 0));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_emerald")), 6, emerald, new Color(27, 209, 45));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_quartz_nether")), 7, quartz, new Color(205, 205, 205));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_quartz_deltas")), 7, quartz, new Color(205, 205, 205));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_ancient_debris_small")), 7, debris, new Color(209, 27, 245));
        registerOre(featureToOre, features, RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla("ore_ancient_debris_large")), 7, debris, new Color(209, 27, 245));
    
        Map<RegistryKey<Biome>, List<Ore>> biomeOreMap = new HashMap<>();
        registry.getOrThrow(RegistryKeys.BIOME).streamEntries().forEach(biomeEntry -> {
            biomeOreMap.put(biomeEntry.getKey().get(), new ArrayList<>());
            biomeEntry.value().getGenerationSettings().getFeatures().stream()
                    .flatMap(RegistryEntryList::stream)
                    .map(RegistryEntry::value)
                    .filter(featureToOre::containsKey)
                    .forEach(feature -> {
                        biomeOreMap.get(biomeEntry.getKey().get()).add(featureToOre.get(feature));
                    });
        });
        return biomeOreMap;
    }
    
    private static void registerOre(Map<PlacedFeature, Ore> map, RegistryWrapper.Impl<PlacedFeature> oreRegistry, RegistryKey<PlacedFeature> oreKey, int genStep, Setting<Boolean> active, Color color) {
        oreRegistry.getOptional(oreKey).ifPresent(orePlacement -> {
            Ore ore = new Ore(orePlacement.value(), genStep, 0, active, color); // index omitted or 0
            map.put(orePlacement.value(), ore);
        });
    }

    public int step;
    public int index;
    public Setting<Boolean> active;
    public IntProvider count = ConstantIntProvider.create(1);
    public HeightProvider heightProvider;
    public HeightContext heightContext;
    public float rarity = 1;
    public float discardOnAirChance;
    public int size;
    public Color color;
    public boolean scattered;

    private Ore(PlacedFeature feature, int step, int index, Setting<Boolean> active, Color color) {
        this.step = step;
        this.index = index;
        this.active = active;
        this.color = color;
        int bottom = mc.world.getBottomY();
        int height = mc.world.getHeight();
        this.heightContext = new HeightContext(null, mc.world);

        for (PlacementModifier modifier : feature.placementModifiers()) {
            if (modifier instanceof CountPlacementModifier cp) {
                this.count = getReflectiveField(cp, "count", "field_35718", IntProvider.class);
            } else if (modifier instanceof HeightRangePlacementModifier hrp) {
                this.heightProvider = getReflectiveField(hrp, "height", "field_35719", HeightProvider.class);
            } else if (modifier instanceof RarityFilterPlacementModifier rf) {
                this.rarity = getReflectiveField(rf, "chance", "field_35720", Float.class);
            }
        }

        FeatureConfig featureConfig = feature.feature().value().config();
        if (featureConfig instanceof OreFeatureConfig oreFeatureConfig) {
            this.discardOnAirChance = getReflectiveField(oreFeatureConfig, "discardOnAirExposureChance", "field_29824", Float.class);
            this.size = getReflectiveField(oreFeatureConfig, "size", "field_13292", Integer.class);
        } else {
            this.size = 1;
        }

        // Using feature type check
        if (feature.feature().value().feature() instanceof OreFeature) {
            // Check if it's a scattered one by checking config or class if needed
            this.scattered = false; 
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getReflectiveField(Object obj, String fieldName, String intermediary, Class<T> type) {
        try {
            Field f;
            try { f = obj.getClass().getDeclaredField(fieldName); }
            catch (NoSuchFieldException e) { f = obj.getClass().getDeclaredField(intermediary); }
            f.setAccessible(true);
            return (T) f.get(obj);
        } catch (Exception e) {
            if (type == Float.class) return (T) Float.valueOf(1F);
            if (type == IntProvider.class) return (T) ConstantIntProvider.create(1);
            return null;
        }
    }
}
