package paladi.client.systems.modules.krypton;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.InvUtils;
import net.minecraft.item.Items;
public class SpearSwap extends Module {
    public SpearSwap() { super(Categories.COMBAT, "spear-swap", "Quickly swaps to trident."); }
    @Override public void onActivate() {
        if (mc.player == null) { toggle(); return; }
        var result = InvUtils.findInHotbar(Items.TRIDENT);
        if (result.found()) { InvUtils.swap(result.slot(), false); info("Switched to trident."); }
        toggle();
    }
}
