package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class Radio extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> volume = sgGeneral.add(new DoubleSetting.Builder().name("volume").defaultValue(0.5).min(0).sliderMax(1).build());
    public Radio() { super(Categories.CLIENT, "radio", "Music player integration for Krypton+."); }
}
