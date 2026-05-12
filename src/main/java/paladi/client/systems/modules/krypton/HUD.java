package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class HUD extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> showCoords = sgGeneral.add(new BoolSetting.Builder().name("show-coords").defaultValue(true).build());
    private final Setting<Boolean> showFPS = sgGeneral.add(new BoolSetting.Builder().name("show-fps").defaultValue(true).build());
    private final Setting<Boolean> showPing = sgGeneral.add(new BoolSetting.Builder().name("show-ping").defaultValue(true).build());
    public HUD() { super(Categories.RENDER, "hud", "Krypton+ HUD overlay configuration."); }
}
