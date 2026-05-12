package paladi.client.systems.oresim;
 
import com.seedfinding.mccore.version.MCVersion;
import net.minecraft.util.Formatting;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
 
public class Seed {
    public final Long seed;
    public final MCVersion version;
 
    public Seed(Long seed, MCVersion version) {
        this.seed = seed;
        this.version = (version == null) ? MCVersion.latest() : version;
    }
 
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.putLong("seed", seed);
        tag.putString("version", version.name);
        return tag;
    }
 
    public static Seed fromTag(NbtCompound tag) {
        return new Seed(
            tag.getLong("seed").get(),
            MCVersion.fromString(tag.getString("version").orElse(""))
        );
    }
 
    public Text toText() {
        MutableText text = Text.literal(String.format("[%s%s%s] (%s)",
            Formatting.GREEN,
            seed.toString(),
            Formatting.WHITE,
            version.toString()
        ));
        text.setStyle(text.getStyle()
            .withClickEvent(new ClickEvent.CopyToClipboard(seed.toString()))
            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Copy to clipboard")))
        );
        return text;
    }
}
