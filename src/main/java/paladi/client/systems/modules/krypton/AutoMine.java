package paladi.client.systems.modules.krypton;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import paladi.client.events.world.TickEvent;
import paladi.client.pathing.SmartNavigator;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.world.BlockUtils;

public class AutoMine extends Module {
    private static final int FOOTPRINT_RADIUS = 1;
    private static final int LAYER_COUNT = 3;
    private static final int MAX_PATH_MINE_DISTANCE = 5;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch-tool")
        .description("Tự động chuyển sang công cụ đào nhanh nhất.")
        .defaultValue(true)
        .build()
    );

    public AutoMine() {
        super(Categories.MISC, "auto-mine-basic", "Tự động đào khối đơn lẻ dưới tâm ngắm (đào cơ bản).");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        BlockPos target = findPatternTarget();
        if (target == null && mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            target = bhr.getBlockPos();
        }

        if (target == null) return;
        if (!shouldMine(target)) return;
        if (mc.world.getBlockState(target).isAir() || mc.world.getBlockState(target).getBlock() == Blocks.BEDROCK) return;

        if (autoSwitch.get()) {
            var tool = InvUtils.findFastestTool(mc.world.getBlockState(target));
            if (tool.found()) InvUtils.swap(tool.slot(), false);
        }

        BlockUtils.breakBlock(target, true);
    }

    private BlockPos findPatternTarget() {
        if (mc.player == null || mc.world == null) return null;

        BlockPos origin = mc.player.getBlockPos();
        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            int y = origin.getY() - layer;
            for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx++) {
                for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz++) {
                    BlockPos pos = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                    if (isMineable(pos)) return pos;
                }
            }
        }

        return null;
    }

    private boolean isMineable(BlockPos pos) {
        if (pos == null || !mc.world.isChunkLoaded(pos)) return false;
        if (mc.world.getBlockState(pos).isAir()) return false;
        if (mc.world.getBlockState(pos).getBlock() == Blocks.BEDROCK) return false;
        if (pos.getY() < mc.world.getBottomY()) return false;

        if (SmartNavigator.get().isNavigating()) {
            BlockPos goal = SmartNavigator.get().getGoal();
            if (goal != null && pos.getManhattanDistance(goal) > MAX_PATH_MINE_DISTANCE) {
                return false;
            }
        }

        return true;
    }

    private boolean shouldMine(BlockPos pos) {
        if (!SmartNavigator.get().isNavigating()) return true;

        BlockPos goal = SmartNavigator.get().getGoal();
        if (goal == null) return true;

        int distToGoal = pos.getManhattanDistance(goal);
        int distToPlayer = pos.getManhattanDistance(mc.player.getBlockPos());

        return distToGoal <= MAX_PATH_MINE_DISTANCE || distToPlayer <= MAX_PATH_MINE_DISTANCE;
    }
}
