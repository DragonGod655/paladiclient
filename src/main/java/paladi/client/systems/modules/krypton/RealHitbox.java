package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class RealHitbox extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final Setting<Boolean> showAll = sgGeneral.add(new BoolSetting.Builder().name("show-all").defaultValue(true).build());
    public RealHitbox() { super(Categories.RENDER, "real-hitbox", "Shows accurate entity hitboxes."); }
}
