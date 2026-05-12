package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class ElytraSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> autoEquip = sgGeneral.add(new BoolSetting.Builder().name("auto-equip").defaultValue(true).build());
    public ElytraSwap() { super(Categories.COMBAT, "elytra-swap", "Quickly swaps between chestplate and elytra."); }
    @Override public void onActivate() {
        if (mc.player == null) { toggle(); return; }
        info("Use ChestSwap module for armor swapping.");
        toggle();
    }
}
