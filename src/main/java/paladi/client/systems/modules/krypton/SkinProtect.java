package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class SkinProtect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> hideAll = sgGeneral.add(new BoolSetting.Builder().name("hide-all-layers").defaultValue(true).build());
    public SkinProtect() { super(Categories.MISC, "skin-protect", "Hides skin layers to prevent skin-based tracking."); }
}
