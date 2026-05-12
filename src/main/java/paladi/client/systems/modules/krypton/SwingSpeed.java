package paladi.client.systems.modules.krypton;

import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;

public class SwingSpeed extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> speed = sgGeneral.add(new IntSetting.Builder()
        .name("swing-speed").description("Hand swing speed. Lower = faster.").defaultValue(6).min(1).sliderMax(20).build()
    );

    public SwingSpeed() {
        super(Categories.RENDER, "swing-speed", "Changes the hand swing animation speed.");
    }

    public int getSwingSpeed() { return isActive() ? speed.get() : -1; }
}
