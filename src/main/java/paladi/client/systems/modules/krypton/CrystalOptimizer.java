package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class CrystalOptimizer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> fastPlace = sgGeneral.add(new BoolSetting.Builder().name("fast-place").defaultValue(true).build());
    private final Setting<Boolean> fastBreak = sgGeneral.add(new BoolSetting.Builder().name("fast-break").defaultValue(true).build());
    public CrystalOptimizer() { super(Categories.COMBAT, "crystal-optimizer", "Optimizes crystal PvP speed."); }
}
