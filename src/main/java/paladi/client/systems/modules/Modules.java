package paladi.client.systems.modules;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import paladi.client.systems.System;
import paladi.client.systems.Systems;
import paladi.client.systems.oresim.OreSim;
import paladi.client.systems.modules.krypton.*;
import paladi.client.systems.modules.combat.*;
import paladi.client.systems.modules.misc.*;
import paladi.client.events.game.GameJoinedEvent;
import paladi.client.events.game.GameLeftEvent;
import paladi.client.PaladiClient;
import meteordevelopment.orbit.EventHandler;
import paladi.client.systems.modules.movement.*;
import paladi.client.systems.modules.player.*;
import paladi.client.systems.modules.render.*;
import paladi.client.systems.modules.render.blockesp.*;
import paladi.client.systems.modules.world.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Pair;

import java.util.*;

public class Modules extends System<Modules> {
    private final Map<Class<? extends Module>, Module> moduleInstances = new Reference2ReferenceOpenHashMap<>();
    private final Map<Category, List<Module>> groups = new Reference2ReferenceOpenHashMap<>();
    private final List<Module> active = new ArrayList<>();
    
    private Module moduleToBind;

    public Modules() {
        super("modules");
    }

    public static Modules get() {
        return Systems.get(Modules.class);
    }

    @Override
    public void init() {
        initInternal();

        groups.clear();
        initCombat();
        initMisc();
        initDonut();
        initBaseFinding();
        initRender();
        initClient();
    }

    private void initInternal() {
        // Combat
        addInternal(new CrystalAura());
        addInternal(new KillAura());
        addInternal(new AutoArmor());
        addInternal(new AutoTotem());
        addInternal(new AnchorAura());
        addInternal(new AntiAnchor());
        addInternal(new AntiAnvil());
        addInternal(new AntiBed());
        addInternal(new ArrowDodge());
        addInternal(new AttributeSwap());
        addInternal(new AutoAnvil());
        addInternal(new AutoCity());
        addInternal(new AutoEXP());
        addInternal(new AutoLog());
        addInternal(new AutoTrap());
        addInternal(new AutoWeapon());
        addInternal(new AutoWeb());
        addInternal(new BedAura());
        addInternal(new BowAimbot());
        addInternal(new BowSpam());
        addInternal(new Burrow());
        addInternal(new Criticals());
        addInternal(new Hitboxes());
        addInternal(new HoleFiller());
        addInternal(new Offhand());
        addInternal(new Quiver());
        addInternal(new SelfAnvil());
        addInternal(new SelfTrap());
        addInternal(new SelfWeb());
        addInternal(new Surround());
        addInternal(new TotemInventory());

        // Misc
        addInternal(new AntiPacketKick());
        addInternal(new AutoReconnect());
        addInternal(new BetterBeacons());
        addInternal(new BetterChat());
        addInternal(new BookBot());
        addInternal(new DiscordPresence());
        addInternal(new DurabilityAlert());
        addInternal(new InventoryTweaks());
        addInternal(new MessageAura());
        addInternal(new Notebot());
        addInternal(new Notifier());
        addInternal(new PacketCanceller());
        addInternal(new PacketLogger());
        addInternal(new ServerSpoof());
        addInternal(new SoundBlocker());
        addInternal(new Spam());
        addInternal(new ShaftFinder());
        addInternal(new paladi.client.systems.modules.misc.swarm.Swarm());
        addInternal(new ChibiDebugger());
        addInternal(new ShaftFinder());
        addInternal(new KryptonAutoShift());
        addInternal(new KryptonAutoBuy());

        // Movement
        addInternal(new AirJump());
        addInternal(new Anchor());
        addInternal(new AntiVoid());
        addInternal(new AutoJump());
        addInternal(new AutoWalk());
        addInternal(new AutoWasp());
        addInternal(new Blink());
        addInternal(new ClickTP());
        addInternal(new ElytraBoost());
        addInternal(new EntityControl());
        addInternal(new FastClimb());
        addInternal(new Flight());
        addInternal(new GUIMove());
        addInternal(new HighJump());
        addInternal(new Jesus());
        addInternal(new LongJump());
        addInternal(new NoFall());
        addInternal(new NoSlow());
        addInternal(new Parkour());
        addInternal(new ReverseStep());
        addInternal(new SafeWalk());
        addInternal(new Scaffold());
        addInternal(new Slippy());
        addInternal(new Sneak());
        addInternal(new Spider());
        addInternal(new Sprint());
        addInternal(new Step());
        addInternal(new TridentBoost());
        addInternal(new Velocity());
        addInternal(new paladi.client.systems.modules.movement.elytrafly.ElytraFly());
        addInternal(new paladi.client.systems.modules.movement.speed.Speed());

        // Player
        addInternal(new AirPlace());
        addInternal(new AntiAFK());
        addInternal(new AntiHunger());
        addInternal(new AutoClicker());
        addInternal(new AutoEat());
        addInternal(new AutoFish());
        addInternal(new AutoGap());
        addInternal(new AutoMend());
        addInternal(new AutoReplenish());
        addInternal(new AutoRespawn());
        addInternal(new AutoTool());
        addInternal(new BreakDelay());
        addInternal(new ChestSwap());
        addInternal(new EXPThrower());
        addInternal(new FakePlayer());
        addInternal(new FastUse());
        addInternal(new GhostHand());
        addInternal(new InstantRebreak());
        addInternal(new LiquidInteract());
        addInternal(new MiddleClickExtra());
        addInternal(new Multitask());
        addInternal(new NameProtect());
        addInternal(new NoInteract());
        addInternal(new NoMiningTrace());
        addInternal(new NoRotate());
        addInternal(new NoStatusEffects());
        addInternal(new OffhandCrash());
        addInternal(new Portals());
        addInternal(new PotionSaver());
        addInternal(new Reach());
        addInternal(new Rotation());
        addInternal(new SpeedMine());

        // Render
        addInternal(new BetterTab());
        addInternal(new BetterTooltips());
        addInternal(new paladi.client.systems.modules.render.blockesp.BlockESP());
        addInternal(new BlockSelection());
        addInternal(new Blur());
        addInternal(new BossStack());
        addInternal(new Breadcrumbs());
        addInternal(new BreakIndicators());
        addInternal(new CameraTweaks());
        addInternal(new Chams());
        addInternal(new ChunkFinder());
        addInternal(new CityESP());
        addInternal(new EntityOwner());
        addInternal(new ESP());
        addInternal(new FreeLook());
        addInternal(new Freecam());
        addInternal(new Fullbright());
        addInternal(new JumpCircles());
        addInternal(new TargetHUD());
        addInternal(new Xray());
        addInternal(new HandView());
        addInternal(new HoleESP());
        addInternal(new ItemHighlight());
        addInternal(new ItemPhysics());
        addInternal(new LightOverlay());
        addInternal(new LogoutSpots());
        addInternal(new paladi.client.systems.modules.render.marker.Marker());
        addInternal(new Nametags());
        addInternal(new NoRender());
        addInternal(new PopChams());
        addInternal(new StorageESP());
        addInternal(new TimeChanger());
        addInternal(new Tracers());
        addInternal(new Trail());
        addInternal(new Trajectories());
        addInternal(new TunnelESP());
        addInternal(new VoidESP());
        addInternal(new WallHack());
        addInternal(new WaypointsModule());
        addInternal(new Zoom());
        addInternal(new CamouflageChunkFinder());

        // World
        addInternal(new Ambience());
        addInternal(new AutoBreed());
        addInternal(new AutoBrewer());
        addInternal(new AutoMount());
        addInternal(new AutoNametag());
        addInternal(new AutoShearer());
        addInternal(new AutoSign());
        addInternal(new AutoSmelter());
        addInternal(new BuildHeight());
        addInternal(new Collisions());
        addInternal(new EChestFarmer());
        addInternal(new EndermanLook());
        addInternal(new Excavator());
        addInternal(new Flamethrower());
        addInternal(new HighwayBuilder());
        addInternal(new InfinityMiner());
        addInternal(new LiquidFiller());
        addInternal(new MountBypass());
        addInternal(new NoGhostBlocks());
        addInternal(new Nuker());
        addInternal(new PacketMine());
        addInternal(new SpawnProofer());
        addInternal(new StashFinder());
        addInternal(new paladi.client.systems.modules.world.Timer());
        addInternal(new VeinMiner());
    }

    private void addInternal(Module module) {
        if (moduleInstances.containsKey(module.getClass())) return;
        moduleInstances.put(module.getClass(), module);
    }

    private void add(Module module) {
        addInternal(module);
        groups.computeIfAbsent(module.category, c -> new ArrayList<>()).add(module);
    }

    private void initCombat() {
        add(new KillAura()); // AIM ASSIST
        add(new AnchorAura()); // ANCHOR MACRO
        add(new AutoDoubleHand());
        add(new CrystalAura()); // AUTO CRYSTAL
        add(new AutoHitCrystal());
        add(new AutoInvTotem());
        add(new AutoJumpReset());
        add(new AutoTotem());
        add(new CrystalOptimizer());
        add(new DoubleAnchor());
        add(new ElytraSwap());
        add(new Hitboxes()); // HITBOX
        add(new HoverTotem());
        add(new TotemOffhand());
        add(new MaceSwap());
        add(new SpearSwap());
        add(new NoHitDelay());
        add(new ShieldBreaker());
        add(new StaticHitboxes());
        add(new TriggerBot());
        add(new MaceBomber());
    }

    private void initMisc() {
        add(new AutoClicker());
        add(new AutoEat());
        add(new AutoFirework());
        add(new AutoLog());
        add(new AutoLoot());
        add(new AutoMine());
        add(new AutoTool());
        add(new AutoTPA());
        add(new CordSnapper());
        add(new ElytraGlide());
        add(new FastUse());
        add(new Freecam());
        add(new KeyPearl());
        add(new KeyWindCharge());
        add(new NameProtect());
        add(new Sprint());
        add(new SkinProtect());
        add(new AutoReconnect());
        add(new ChibiDebugger());
        add(new ShaftFinder());
        add(new KryptonAutoShift());
    }

    private void initDonut() {
        add(new AntiTrap());
        add(new AuctionSniper());
        add(new AutoSell());
        add(new AutoSpawnerSell());
        add(new ItemDropper());
        add(new NetheriteFinder());
        add(new RTPBaseFinder());
        add(new AutoShulkerBuy());
        add(new TunnelBaseFinder());
        add(new FakeStats());
        add(new SpawnerProtect());
        add(new ChunkFinder());
        add(new SusChunkFinder());
        add(new KryptonAutoBuy());
    }

    private void initBaseFinding() {
        add(new SeedChunkFinder());
        add(new HoleESP());
        add(new LightOverlay()); // LIGHT FINDER
        add(new JungleFinder());
        add(new SuspiciousESP());
        add(new CamouflageChunkFinder());
    }

    private void initRender() {
        add(new OreSim());
        add(new Fullbright());
        add(new SwingSpeed());
        add(new JumpCircles());
        add(new HUD());
        add(new ESP());
        add(new StorageESP());
        add(new KryptonStorageESP());
        add(new Tracers());
        add(new TargetHUD());
        add(new RealHitbox());
        add(new FreeLook());
    }

    private void initClient() {
        add(new KryptonArrayList()); // KRYPTON+
        add(new ChatMacro());
        add(new Radio());
        add(new Friends());
        add(new DiscordPresence());
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        for (Module module : active) {
            if (!module.runInMainMenu) {
                if (module.autoSubscribe) PaladiClient.EVENT_BUS.subscribe(module);
                module.onActivate();
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        for (Module module : active) {
            if (!module.runInMainMenu) {
                if (module.autoSubscribe) PaladiClient.EVENT_BUS.unsubscribe(module);
                module.onDeactivate();
            }
        }
    }

    public boolean isActive(Class<? extends Module> klass) {
        Module m = get(klass);
        return m != null && m.isActive();
    }

    public void addActive(Module module) {
        if (!active.contains(module)) {
            active.add(module);
            active.sort(Comparator.comparing(m -> m.title));
        }
    }

    public void removeActive(Module module) {
        active.remove(module);
    }

    public void setModuleToBind(Module module) {
        this.moduleToBind = module;
    }

    public void awaitKeyRelease() {
    }

    public boolean isBinding() {
        return moduleToBind != null;
    }

    public int getCount() {
        return moduleInstances.size();
    }

    public void sortModules() {
        for (List<Module> modules : groups.values()) {
            modules.sort(Comparator.comparing(o -> o.title));
        }
    }

    public List<Pair<Module, String>> searchTitles(String text) {
        List<Pair<Module, String>> results = new ArrayList<>();
        for (Module m : getAll()) {
            if (m.title.toLowerCase().contains(text.toLowerCase())) {
                results.add(new Pair<>(m, m.title));
            }
        }
        return results;
    }

    public Set<Module> searchSettingTitles(String text) {
        return new HashSet<>();
    }

    public <T extends Module> T get(Class<T> klass) {
        return (T) moduleInstances.get(klass);
    }

    public Module get(String name) {
        for (Module m : getAll()) {
            if (m.name.equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    public Collection<Module> getAll() {
        return moduleInstances.values();
    }

    public List<Module> getActive() {
        return active;
    }

    public List<Module> getGroup(Category category) {
        return groups.getOrDefault(category, Collections.emptyList());
    }

    public static List<Category> loopCategories() {
        return Arrays.asList(Categories.COMBAT, Categories.MISC, Categories.DONUT, Categories.BASEFINDING, Categories.RENDER, Categories.CLIENT);
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        NbtList modulesTag = new NbtList();
        for (Module module : getAll()) {
            NbtCompound moduleTag = module.toTag();
            if (moduleTag != null) modulesTag.add(moduleTag);
        }
        tag.put("modules", modulesTag);
        return tag;
    }

    @Override
    public Modules fromTag(NbtCompound tag) {
        NbtList modulesTag = tag.getList("modules").orElse(new NbtList());
        for (NbtElement moduleTag : modulesTag) {
            NbtCompound mTag = (NbtCompound) moduleTag;
            Module module = get(mTag.getString("name").orElse(""));
            if (module != null) module.fromTag(mTag);
        }
        return this;
    }
}
