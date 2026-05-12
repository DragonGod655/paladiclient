package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class AutoMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder().name("auto-switch-tool").defaultValue(true).build());
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").defaultValue(4.5).min(1).sliderMax(6).build());
    public AutoMine() { super(Categories.MISC, "auto-mine", "Automatically mines blocks you look at."); }
}
