package paladi.client.systems.modules.render;

import paladi.client.events.render.Render2DEvent;
import paladi.client.events.render.Render3DEvent;
import paladi.client.renderer.ShapeMode;
import paladi.client.renderer.text.TextRenderer;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.Utils;
import paladi.client.utils.player.PlayerUtils;
import paladi.client.utils.render.NametagUtils;
import paladi.client.utils.render.color.Color;
import paladi.client.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class KryptonStorageESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General
    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws lines to containers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> labels = sgGeneral.add(new BoolSetting.Builder()
        .name("labels")
        .description("Shows name and distance labels.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> labelScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("label-scale")
        .description("Size of the labels.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(5.0)
        .visible(labels::get)
        .build()
    );

    // Colors (Krypton Palette)
    private final Setting<SettingColor> shulkerColor = sgColors.add(new ColorSetting.Builder()
        .name("shulker-color")
        .description("Color for Shulker Boxes (Critical).")
        .defaultValue(new SettingColor(225, 25, 25, 255))
        .build()
    );

    private final Setting<SettingColor> enderColor = sgColors.add(new ColorSetting.Builder()
        .name("ender-color")
        .description("Color for Ender Chests (Critical).")
        .defaultValue(new SettingColor(225, 25, 25, 255))
        .build()
    );

    private final Setting<SettingColor> chestColor = sgColors.add(new ColorSetting.Builder()
        .name("chest-color")
        .description("Color for normal Chests/Barrels (High).")
        .defaultValue(new SettingColor(255, 160, 0, 255))
        .build()
    );

    private final Setting<SettingColor> otherColor = sgColors.add(new ColorSetting.Builder()
        .name("other-color")
        .description("Color for other machines (Medium).")
        .defaultValue(new SettingColor(203, 90, 203, 255))
        .build()
    );

    public KryptonStorageESP() {
        super(Categories.Render, "krypton-storage-esp", "Premium storage ESP with Krypton aesthetics.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (BlockEntity blockEntity : Utils.blockEntities()) {
            BlockPos pos = blockEntity.getPos();
            SettingColor color = getBlockColor(blockEntity);
            if (color == null) continue;

            double x = pos.getX();
            double y = pos.getY();
            double z = pos.getZ();

            // Render Box
            Color sideColor = new Color(color);
            sideColor.a = 50;
            event.renderer.box(x, y, z, x + 1, y + 1, z + 1, sideColor, color, shapeMode.get(), 0);

            // Render Tracer
            if (tracers.get()) {
                Vec3d center = new Vec3d(x + 0.5, y + 0.5, z + 0.5);
                Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta).add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);
                event.renderer.line(playerPos.x, playerPos.y, playerPos.z, center.x, center.y, center.z, color);
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!labels.get()) return;

        TextRenderer text = TextRenderer.get();
        for (BlockEntity blockEntity : Utils.blockEntities()) {
            BlockPos pos = blockEntity.getPos();
            SettingColor color = getBlockColor(blockEntity);
            if (color == null) continue;

            Vector3d worldPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            
            if (NametagUtils.to2D(worldPos, labelScale.get())) {
                double dist = PlayerUtils.distanceToCamera(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (dist < 1) continue;

                String labelText = getBlockName(blockEntity) + " [" + (int)dist + "m]";
                
                NametagUtils.begin(worldPos);
                text.begin();
                text.render(labelText, -text.getWidth(labelText) / 2, 0, color, true);
                text.end();
                NametagUtils.end();
            }
        }
    }

    private SettingColor getBlockColor(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity) return shulkerColor.get();
        if (be instanceof EnderChestBlockEntity) return enderColor.get();
        if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) return chestColor.get();
        if (be instanceof AbstractFurnaceBlockEntity || be instanceof DispenserBlockEntity || be instanceof HopperBlockEntity) return otherColor.get();
        return null;
    }

    private String getBlockName(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity) return "Shulker";
        if (be instanceof EnderChestBlockEntity) return "Ender Chest";
        if (be instanceof ChestBlockEntity) return "Chest";
        if (be instanceof BarrelBlockEntity) return "Barrel";
        if (be instanceof FurnaceBlockEntity) return "Furnace";
        if (be instanceof HopperBlockEntity) return "Hopper";
        if (be instanceof DispenserBlockEntity) return "Dispenser";
        if (be instanceof DropperBlockEntity) return "Dropper";
        return "Storage";
    }
}
