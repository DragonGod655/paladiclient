package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class HoverTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> healthThreshold = sgGeneral.add(new DoubleSetting.Builder().name("health").defaultValue(6).min(1).sliderMax(20).build());
    public HoverTotem() { super(Categories.COMBAT, "hover-totem", "Keeps totem ready when health is low."); }
}
