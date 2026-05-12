package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class StaticHitboxes extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final Setting<Double> size = sgGeneral.add(new DoubleSetting.Builder().name("size").defaultValue(0.4).min(0.1).sliderMax(2).build());
    public StaticHitboxes() { super(Categories.COMBAT, "static-hitboxes", "Makes entity hitboxes a fixed size for easier targeting."); }
    public double getSize() { return isActive() ? size.get() : -1; }
}
