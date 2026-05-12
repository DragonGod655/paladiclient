package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class JungleFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> scanRange = sgGeneral.add(new IntSetting.Builder().name("scan-range").defaultValue(256).min(64).sliderMax(1024).build());
    public JungleFinder() { super(Categories.BASEFINDING, "jungle-finder", "Helps locate jungle biomes for base hunting."); }
}
