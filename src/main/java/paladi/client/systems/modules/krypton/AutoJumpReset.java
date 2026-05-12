package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class AutoJumpReset extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> onHit = sgGeneral.add(new BoolSetting.Builder().name("on-hit").defaultValue(true).build());
    public AutoJumpReset() { super(Categories.COMBAT, "auto-jump-reset", "Resets attack cooldown by jumping."); }
}
