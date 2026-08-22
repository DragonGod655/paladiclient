package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class KeyWindCharge extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoSwapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-swap-back")
        .description("Swaps back to previous slot after throwing wind charge.")
        .defaultValue(true)
        .build()
    );

    private boolean shouldSwapBack = false;
    private int delayTicks = 0;

    public KeyWindCharge() {
        super(Categories.MISC, "key-wind-charge", "Throws a wind charge instantly when keybind is pressed.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        throwWindCharge();
    }

    private void throwWindCharge() {
        FindItemResult result = InvUtils.findInHotbar(Items.WIND_CHARGE);
        if (!result.found()) {
            result = InvUtils.find(Items.WIND_CHARGE);
        }

        if (!result.found()) {
            warning("No Wind Charges found!");
            toggle();
            return;
        }

        if (result.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            toggle();
        } else if (result.isHotbar()) {
            InvUtils.swap(result.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (autoSwapBack.get()) {
                shouldSwapBack = true;
                delayTicks = 1;
            } else {
                toggle();
            }
        } else {
            int prevSlot = mc.player.getInventory().getSelectedSlot();
            InvUtils.quickSwap().fromId(prevSlot).to(result.slot());
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (autoSwapBack.get()) {
                shouldSwapBack = true;
                delayTicks = 1;
            } else {
                toggle();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (shouldSwapBack) {
            if (delayTicks > 0) {
                delayTicks--;
                return;
            }

            InvUtils.swapBack();
            shouldSwapBack = false;
            toggle();
        }
    }
}
