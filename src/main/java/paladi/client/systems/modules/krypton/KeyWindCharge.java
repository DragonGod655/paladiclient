package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class KeyWindCharge extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> autoSwapBack = sgGeneral.add(new BoolSetting.Builder().name("auto-swap-back").defaultValue(true).build());
    private boolean shouldThrow = false;

    public KeyWindCharge() { super(Categories.MISC, "key-wind-charge", "Throws a wind charge when the module keybind is pressed."); }

    @Override public void onActivate() { shouldThrow = true; }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || !shouldThrow) return;
        var result = InvUtils.findInHotbar(Items.WIND_CHARGE);
        if (result.found()) {
            InvUtils.swap(result.slot(), autoSwapBack.get());
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (autoSwapBack.get()) InvUtils.swapBack();
        }
        shouldThrow = false;
        toggle();
    }
}
