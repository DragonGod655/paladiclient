package paladi.client.systems.modules.krypton;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
public class AuctionSniper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<String> keyword = sgGeneral.add(new StringSetting.Builder().name("keyword").defaultValue("").build());
    private final Setting<Integer> maxPrice = sgGeneral.add(new IntSetting.Builder().name("max-price").defaultValue(1000).min(1).sliderMax(100000).build());
    public AuctionSniper() { super(Categories.DONUT, "auction-sniper", "Automatically bids on auction items matching your keywords."); }
}
