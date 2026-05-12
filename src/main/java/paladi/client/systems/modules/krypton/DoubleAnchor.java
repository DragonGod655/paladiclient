package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.player.PlayerUtils;

import static paladi.client.PaladiClient.mc;

public class DoubleAnchor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The range at which to perform the macro.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private int timer;
    private int stage;

    public DoubleAnchor() {
        super(Categories.COMBAT, "double-anchor", "Places and charges two respawn anchors quickly.");
    }

    @Override
    public void onActivate() {
        timer = 1; // wait 0.05s
        stage = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (timer > 0) {
            timer--;
            return;
        }

        if (mc.crosshairTarget == null || !(mc.crosshairTarget instanceof BlockHitResult hit)) {
            toggle();
            return;
        }

        if (PlayerUtils.distanceTo(hit.getBlockPos()) > range.get()) {
            toggle();
            return;
        }

        FindItemResult anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
        FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);

        if (!anchor.found() || !glowstone.found()) {
            toggle();
            return;
        }

        switch (stage) {
            case 0 -> { // switch #anchor then input right
                InvUtils.swap(anchor.slot(), false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                timer = 1;
                stage++;
            }
            case 1 -> { // switch #glowstone then input right
                InvUtils.swap(glowstone.slot(), false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                timer = 1;
                stage++;
            }
            case 2 -> { // switch #anchor then loop 2 input right
                InvUtils.swap(anchor.slot(), false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit); // Explode 1
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit); // Place 2
                mc.player.swingHand(Hand.MAIN_HAND);
                timer = 1;
                stage++;
            }
            case 3 -> { // switch #glowstone then input right
                InvUtils.swap(glowstone.slot(), false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                timer = 1;
                stage++;
            }
            case 4 -> { // switch #anchor then input right
                InvUtils.swap(anchor.slot(), false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                toggle();
            }
        }
    }
}
