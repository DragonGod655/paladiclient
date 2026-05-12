package paladi.client.systems.oresim;
 
import paladi.client.PaladiClient;
import paladi.client.systems.System;
import paladi.client.systems.config.Config;
import paladi.client.utils.Utils;
import paladi.client.utils.player.ChatUtils;
import com.seedfinding.mccore.version.MCVersion;
import net.minecraft.util.Formatting;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
 
import java.util.HashMap;
 
import static paladi.client.PaladiClient.mc;
 
public class Seeds extends System<Seeds> {
    private static final Seeds INSTANCE = new Seeds();
 
    public HashMap<String, Seed> seeds = new HashMap<>();
 
    public Seeds() {
        super("seeds");
    }
 
    public static Seeds get() {
        return INSTANCE;
    }
 
    public Seed getSeed() {
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            MCVersion version = MCVersion.fromString(mc.getServer().getVersion());
            if (version == null) version = MCVersion.latest();
            return new Seed(mc.getServer().getOverworld().getSeed(), version);
        }
        return seeds.get(Utils.getWorldName());
    }
 
    public void setSeed(String seed, MCVersion version) {
        if (mc.isInSingleplayer()) return;
        long numSeed = toSeed(seed);
        seeds.put(Utils.getWorldName(), new Seed(numSeed, version));
        PaladiClient.EVENT_BUS.post(SeedChangedEvent.get(numSeed));
    }
 
    public void setSeed(String seed) {
        if (mc.isInSingleplayer()) return;
        ServerInfo server = mc.getCurrentServerEntry();
        MCVersion ver = null;
        if (server != null) ver = MCVersion.fromString(server.version.getString());
        if (ver == null) {
            String targetVer = (server != null) ? server.version.getString() : "unknown";
            sendInvalidVersionWarning(seed, targetVer);
            ver = MCVersion.latest();
        }
        setSeed(seed, ver);
    }
 
    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        seeds.forEach((key, seed) -> {
            if (seed != null) tag.put(key, seed.toTag());
        });
        return tag;
    }
 
    @Override
    public Seeds fromTag(NbtCompound tag) {
        tag.getKeys().forEach(key -> {
            seeds.put(key, Seed.fromTag(tag.getCompound(key).orElse(new NbtCompound())));
        });
        return this;
    }
 
    private static long toSeed(String inSeed) {
        try {
            return Long.parseLong(inSeed);
        } catch (NumberFormatException e) {
            return inSeed.trim().hashCode();
        }
    }
 
    private static void sendInvalidVersionWarning(String seed, String targetVer) {
        MutableText msg = Text.literal(String.format("Couldn't resolve minecraft version \"%s\". Using %s instead. If you wish to change the version run: ", targetVer, MCVersion.latest().name));
        String cmd = String.format("%sseed %s ", Config.get().prefix, seed);
        MutableText cmdText = Text.literal(cmd + "<version>");
        cmdText.setStyle(cmdText.getStyle()
            .withUnderline(true)
            .withClickEvent(new ClickEvent.SuggestCommand(cmd))
            .withHoverEvent(new HoverEvent.ShowText(Text.literal("run command")))
        );
        msg.append(cmdText);
        msg.setStyle(msg.getStyle().withColor(Formatting.YELLOW));
        ChatUtils.sendMsg("Seed", msg);
    }
}
