package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class SuspiciousESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").defaultValue(64).min(16).sliderMax(256).build());
    private final Setting<Boolean> highlightContainers = sgGeneral.add(new BoolSetting.Builder().name("highlight-containers").defaultValue(true).build());
    public SuspiciousESP() { super(Categories.BASEFINDING, "suspicious-esp", "ESP overlay for suspicious blocks indicating player activity."); }
}
