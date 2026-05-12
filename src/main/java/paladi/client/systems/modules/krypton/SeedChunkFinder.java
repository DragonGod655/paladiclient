package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class SeedChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<String> seed = sgGeneral.add(new StringSetting.Builder().name("seed").defaultValue("").build());
    public SeedChunkFinder() { super(Categories.BASEFINDING, "seed-chunk-finder", "Uses world seed to predict chunk generation patterns."); }
}
