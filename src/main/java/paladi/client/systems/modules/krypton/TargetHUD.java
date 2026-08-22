package paladi.client.systems.modules.krypton;

import paladi.client.events.render.Render2DEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import paladi.client.utils.entity.TargetUtils;
import paladi.client.utils.entity.SortPriority;
import paladi.client.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import static paladi.client.PaladiClient.mc;

public class TargetHUD extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the Target HUD.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(2.0)
        .build()
    );

    public TargetHUD() {
        super(Categories.RENDER, "target-hud", "Displays professional info about your target.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

        PlayerEntity target = TargetUtils.getPlayerTarget(10, SortPriority.LowestDistance);
        if (target == null) return;

        double s = scale.get();
        int x = (int) ((event.screenWidth / 2 + 10) / s);
        int y = (int) ((event.screenHeight / 2 + 10) / s);

        event.drawContext.getMatrices().pushMatrix();
        try {
            event.drawContext.getMatrices().scale((float) s, (float) s);

            // Background
            int width = 120;
            int height = 45;
            event.drawContext.fill(x, y, x + width, y + height, new Color(0, 0, 0, 150).getPacked());

            // Name
            event.drawContext.drawTextWithShadow(mc.textRenderer, target.getName().getString(), x + 5, y + 5, Color.WHITE.getPacked());

            // Health Bar
            float healthPercent = Math.max(0, Math.min(1, target.getHealth() / target.getMaxHealth()));
            int barWidth = width - 10;
            event.drawContext.fill(x + 5, y + 18, x + 5 + barWidth, y + 22, new Color(50, 50, 50, 255).getPacked());
            event.drawContext.fill(x + 5, y + 18, x + 5 + (int) (barWidth * healthPercent), y + 22, new Color(0, 255, 100, 255).getPacked());

            // Health Text
            String healthText = String.format("%.1f HP", target.getHealth());
            event.drawContext.drawTextWithShadow(mc.textRenderer, healthText, x + 5, y + 25, Color.WHITE.getPacked());

            // Armor
            int armorX = x + 60;
            int armorY = y + 28;
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < slots.length; i++) {
                ItemStack armor = target.getEquippedStack(slots[i]);
                if (!armor.isEmpty()) {
                    event.drawContext.drawItem(armor, armorX + (i * 15), armorY);
                }
            }
        } finally {
            event.drawContext.getMatrices().popMatrix();
        }
    }
}
