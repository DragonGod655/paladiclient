package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class TotemOffhand extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> swap = sgGeneral.add(new BoolSetting.Builder().name("auto-swap").defaultValue(true).build());
    public TotemOffhand() { super(Categories.COMBAT, "totem-offhand", "Keeps a totem in your offhand at all times."); }
}
