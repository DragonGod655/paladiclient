package paladi.client.systems.modules.krypton;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.InvUtils;
import net.minecraft.item.Items;
public class MaceSwap extends Module {
    public MaceSwap() { super(Categories.COMBAT, "mace-swap", "Quickly swaps to mace."); }
    @Override public void onActivate() {
        if (mc.player == null) { toggle(); return; }
        var result = InvUtils.findInHotbar(Items.MACE);
        if (result.found()) { InvUtils.swap(result.slot(), false); info("Switched to mace."); }
        toggle();
    }
}
