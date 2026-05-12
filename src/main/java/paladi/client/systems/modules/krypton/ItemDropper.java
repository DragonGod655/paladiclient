package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;

public class ItemDropper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between drops in ticks.")
        .defaultValue(2)
        .min(0).sliderMax(20)
        .build()
    );

    private final Setting<Boolean> mainHandOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("main-hand-only")
        .description("Only drop the item in main hand.")
        .defaultValue(true)
        .build()
    );

    private int tickCounter = 0;

    public ItemDropper() {
        super(Categories.DONUT, "item-dropper", "Rapidly drops items from your inventory.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        tickCounter++;
        if (tickCounter < delay.get()) return;
        tickCounter = 0;

        if (mainHandOnly.get()) {
            if (!mc.player.getMainHandStack().isEmpty()) {
                mc.player.dropSelectedItem(true);
            }
        } else {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                        i < 9 ? i + 36 : i, 1, net.minecraft.screen.slot.SlotActionType.THROW, mc.player);
                    break;
                }
            }
        }
    }
}
