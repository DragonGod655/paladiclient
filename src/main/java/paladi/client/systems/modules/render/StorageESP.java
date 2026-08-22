/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import paladi.client.events.entity.player.InteractBlockEvent;
import paladi.client.events.render.Render3DEvent;
import paladi.client.renderer.MeshBuilder;
import paladi.client.renderer.MeshRenderer;
import paladi.client.renderer.PaladiRenderPipelines;
import paladi.client.renderer.ShapeMode;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.Utils;
import paladi.client.utils.player.PlayerUtils;
import paladi.client.utils.render.MeshBuilderVertexConsumerProvider;
import paladi.client.utils.render.RenderUtils;
import paladi.client.utils.render.SimpleBlockRenderer;
import paladi.client.utils.render.color.Color;
import paladi.client.utils.render.color.SettingColor;
import paladi.client.utils.render.postprocess.PostProcessShaders;
import paladi.client.utils.world.Dir;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StorageESP extends Module {
    public enum Mode {
        Box,
        Shader
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgOpened = settings.createGroup("Opened Rendering");

    // General

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Rendering mode.")
        .defaultValue(Mode.Box)
        .build()
    );

    private final Setting<List<BlockEntityType<?>>> storageBlocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("storage-blocks")
        .description("Select the storage blocks to display.")
        .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
        .build()
    );

    public final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(() -> mode.get() == Mode.Box)
        .build()
    );

    public final Setting<Integer> fillOpacity = sgGeneral.add(new IntSetting.Builder()
        .name("fill-opacity")
        .description("The opacity of the shape fill.")
        .defaultValue(50)
        .range(0, 255)
        .sliderMax(255)
        .visible(() -> mode.get() == Mode.Box && shapeMode.get() != ShapeMode.Lines)
        .build()
    );

    public final Setting<Integer> outlineWidth = sgGeneral.add(new IntSetting.Builder()
        .name("width")
        .description("The width of the shader outline.")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 5)
        .visible(() -> mode.get() == Mode.Shader)
        .build()
    );

    public final Setting<Double> glowMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-multiplier")
        .description("Multiplier for glow effect.")
        .defaultValue(3.5)
        .min(0)
        .sliderMax(10)
        .decimalPlaces(3)
        .visible(() -> mode.get() == Mode.Shader)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draws tracers to storage blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> fadeDistance = sgGeneral.add(new IntSetting.Builder()
        .name("fade-distance")
        .description("The distance at which the color will fade.")
        .defaultValue(0)
        .min(0)
        .sliderMax(100)
        .build()
    );

    // Colors

    private final Setting<SettingColor> shulker = sgColors.add(new ColorSetting.Builder()
        .name("shulker")
        .description("The color of Shulker Boxes.")
        .defaultValue(new SettingColor(255, 30, 80, 255))
        .build()
    );

    private final Setting<SettingColor> enderChest = sgColors.add(new ColorSetting.Builder()
        .name("ender-chest")
        .description("The color of Ender Chests.")
        .defaultValue(new SettingColor(170, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> chest = sgColors.add(new ColorSetting.Builder()
        .name("chest")
        .description("The color of normal chests.")
        .defaultValue(new SettingColor(255, 160, 0, 255))
        .build()
    );

    private final Setting<SettingColor> trappedChest = sgColors.add(new ColorSetting.Builder()
        .name("trapped-chest")
        .description("The color of trapped chests.")
        .defaultValue(new SettingColor(255, 50, 0, 255))
        .build()
    );

    private final Setting<SettingColor> barrel = sgColors.add(new ColorSetting.Builder()
        .name("barrel")
        .description("The color of barrels.")
        .defaultValue(new SettingColor(230, 140, 40, 255))
        .build()
    );

    private final Setting<SettingColor> spawner = sgColors.add(new ColorSetting.Builder()
        .name("spawner")
        .description("The color of mob spawners.")
        .defaultValue(new SettingColor(255, 0, 50, 255))
        .build()
    );

    private final Setting<SettingColor> furnace = sgColors.add(new ColorSetting.Builder()
        .name("furnace")
        .description("The color of furnaces, smokers and blast furnaces.")
        .defaultValue(new SettingColor(140, 140, 140, 255))
        .build()
    );

    private final Setting<SettingColor> hopper = sgColors.add(new ColorSetting.Builder()
        .name("hopper")
        .description("The color of hoppers, dispensers and droppers.")
        .defaultValue(new SettingColor(0, 200, 255, 255))
        .build()
    );

    private final Setting<SettingColor> other = sgColors.add(new ColorSetting.Builder()
        .name("other")
        .description("The color of crafters, brewing stands, pots, bookshelves and campfires.")
        .defaultValue(new SettingColor(0, 255, 150, 255))
        .build()
    );

    // Opened

    private final Setting<Boolean> hideOpened = sgOpened.add(new BoolSetting.Builder()
        .name("hide-opened")
        .description("Hides opened containers.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> openedColor = sgOpened.add(new ColorSetting.Builder()
        .name("opened-color")
        .description("Optional setting to change colors of opened chests.")
        .defaultValue(new SettingColor(203, 90, 203, 0))
        .build()
    );

    private final Set<BlockPos> interactedBlocks = new HashSet<>();
    private final Color lineColor = new Color(0, 0, 0, 255);
    private final Color sideColor = new Color(0, 0, 0, 50);
    private boolean render;
    private int count;

    private final MeshBuilder mesh;
    private final MeshBuilderVertexConsumerProvider vertexConsumerProvider;

    public StorageESP() {
        super(Categories.Render, "storage-esp", "Renders all storage blocks through walls.");
        mesh = new MeshBuilder(PaladiRenderPipelines.WORLD_COLORED);
        vertexConsumerProvider = new MeshBuilderVertexConsumerProvider(mesh);
    }

    @Override
    public void onDeactivate() {
        interactedBlocks.clear();
    }

    @EventHandler
    private void onBlockInteract(InteractBlockEvent event) {
        if (event.result == null) return;
        BlockPos pos = event.result.getBlockPos();
        if (mc.world == null) return;
        BlockEntity blockEntity = mc.world.getBlockEntity(pos);

        if (blockEntity == null) return;

        interactedBlocks.add(pos);
        if (blockEntity instanceof ChestBlockEntity chestBlockEntity) {
            BlockState state = chestBlockEntity.getCachedState();
            if (state.contains(ChestBlock.CHEST_TYPE)) {
                ChestType chestType = state.get(ChestBlock.CHEST_TYPE);

                if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                    Direction facing = state.get(ChestBlock.FACING);
                    BlockPos otherPartPos = pos.offset(chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise());
                    interactedBlocks.add(otherPartPos);
                }
            }
        }
    }

    private void getBlockEntityColor(BlockEntity blockEntity) {
        render = false;
        if (blockEntity == null) return;
        if (!storageBlocks.get().contains(blockEntity.getType())) return;

        if (blockEntity instanceof ShulkerBoxBlockEntity) lineColor.set(shulker.get());
        else if (blockEntity instanceof EnderChestBlockEntity) lineColor.set(enderChest.get());
        else if (blockEntity instanceof TrappedChestBlockEntity) lineColor.set(trappedChest.get());
        else if (blockEntity instanceof ChestBlockEntity) lineColor.set(chest.get());
        else if (blockEntity instanceof BarrelBlockEntity) lineColor.set(barrel.get());
        else if (blockEntity instanceof MobSpawnerBlockEntity) lineColor.set(spawner.get());
        else if (blockEntity instanceof AbstractFurnaceBlockEntity) lineColor.set(furnace.get());
        else if (blockEntity instanceof HopperBlockEntity || blockEntity instanceof DispenserBlockEntity || blockEntity instanceof DropperBlockEntity) lineColor.set(hopper.get());
        else if (blockEntity instanceof BrewingStandBlockEntity || blockEntity instanceof ChiseledBookshelfBlockEntity || blockEntity instanceof CrafterBlockEntity || blockEntity instanceof DecoratedPotBlockEntity || blockEntity instanceof CampfireBlockEntity) lineColor.set(other.get());
        else return;

        render = true;
        sideColor.set(lineColor);
        sideColor.a = fillOpacity.get();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        count = 0;

        for (net.minecraft.world.chunk.Chunk chunk : Utils.chunks()) {
            if (!(chunk instanceof net.minecraft.world.chunk.WorldChunk worldChunk)) continue;

            for (BlockEntity blockEntity : worldChunk.getBlockEntities().values()) {
                if (blockEntity == null || blockEntity.getPos() == null) continue;

                BlockPos pos = blockEntity.getPos();

                boolean interacted = interactedBlocks.contains(pos);
                if (interacted && hideOpened.get()) continue;

                getBlockEntityColor(blockEntity);
                if (!render) continue;

                if (interacted && openedColor.get().a > 0) {
                    lineColor.set(openedColor.get());
                    sideColor.set(openedColor.get());
                    sideColor.a = fillOpacity.get();
                }

                int prevLineA = lineColor.a;
                int prevSideA = sideColor.a;

                if (fadeDistance.get() > 0) {
                    double dist = Math.sqrt(PlayerUtils.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                    if (dist > fadeDistance.get()) {
                        double fadeRatio = Math.max(0.0, 1.0 - (dist - fadeDistance.get()) / 10.0);
                        if (fadeRatio <= 0.05) continue;
                        lineColor.a = (int) (lineColor.a * fadeRatio);
                        sideColor.a = (int) (sideColor.a * fadeRatio);
                    }
                }

                if (count == 0 && mode.get() == Mode.Shader) {
                    mesh.begin();
                }

                if (tracers.get()) {
                    Color tc = lineColor.copy();
                    tc.a = 255;
                    event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tc);
                }

                if (mode.get() == Mode.Box) {
                    renderBox(event, blockEntity);
                } else if (mode.get() == Mode.Shader) {
                    renderShader(event, blockEntity);
                }

                lineColor.a = prevLineA;
                sideColor.a = prevSideA;

                count++;
            }
        }

        if (mode.get() == Mode.Shader && count > 0) {
            MeshRenderer.begin()
                .attachments(PostProcessShaders.STORAGE_OUTLINE.framebuffer)
                .clearColor(Color.CLEAR)
                .pipeline(PaladiRenderPipelines.WORLD_COLORED)
                .mesh(mesh, event.matrices)
                .end();

            PostProcessShaders.STORAGE_OUTLINE.render();
        }
    }

    private void renderBox(Render3DEvent event, BlockEntity blockEntity) {
        double x1 = blockEntity.getPos().getX();
        double y1 = blockEntity.getPos().getY();
        double z1 = blockEntity.getPos().getZ();

        double x2 = x1 + 1;
        double y2 = y1 + 1;
        double z2 = z1 + 1;

        int excludeDir = 0;
        if (blockEntity instanceof ChestBlockEntity) {
            BlockState state = mc.world.getBlockState(blockEntity.getPos());
            if (state.getBlock() instanceof ChestBlock && state.contains(ChestBlock.CHEST_TYPE) && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                excludeDir = Dir.get(ChestBlock.getFacing(state));
            }
        }

        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity) {
            double a = 1.0 / 16.0;

            if (Dir.isNot(excludeDir, Dir.WEST)) x1 += a;
            if (Dir.isNot(excludeDir, Dir.NORTH)) z1 += a;

            if (Dir.isNot(excludeDir, Dir.EAST)) x2 -= a;
            y2 -= a * 2;
            if (Dir.isNot(excludeDir, Dir.SOUTH)) z2 -= a;
        }

        event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor, lineColor, shapeMode.get(), excludeDir);
    }

    private void renderShader(Render3DEvent event, BlockEntity blockEntity) {
        vertexConsumerProvider.setColor(lineColor);
        SimpleBlockRenderer.renderWithBlockEntity(blockEntity, event.tickDelta, vertexConsumerProvider);
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }

    public boolean isShader() {
        return isActive() && mode.get() == Mode.Shader;
    }
}
