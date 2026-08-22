package paladi.client.systems.modules.krypton;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import paladi.client.events.render.Render3DEvent;
import paladi.client.events.world.ChunkDataEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.renderer.ShapeMode;
import paladi.client.settings.*;
import paladi.client.systems.friends.Friends;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.misc.AutoReconnect;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import paladi.client.utils.render.color.SettingColor;
import paladi.client.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import static paladi.client.PaladiClient.mc;

public class TunnelBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDigging = settings.createGroup("Auto Digging");
    private final SettingGroup sgSafety  = settings.createGroup("Safety & Auto Log");

    // General Settings
    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build()
    );
    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(255, 100, 0, 130)).build()
    );
    private final Setting<Integer> renderHeight = sgGeneral.add(new IntSetting.Builder()
        .name("height").defaultValue(10).min(0).sliderMax(128).build()
    );
    private final Setting<Integer> minTunnelBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("min-tunnel-blocks").description("Min air blocks in a line to count as tunnel.").defaultValue(8).min(3).sliderMax(32).build()
    );

    // Auto Digging Settings
    private final Setting<Boolean> autoDig = sgDigging.add(new BoolSetting.Builder()
        .name("auto-dig")
        .description("Tự động vừa đi thẳng vừa đào hầm 1x2 hoặc 3x3 để tìm hầm/base.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> snapDirection = sgDigging.add(new BoolSetting.Builder()
        .name("snap-direction")
        .description("Khóa góc nhìn thẳng hướng chính (Bắc/Nam/Đông/Tây) khi bắt đầu đào.")
        .defaultValue(true)
        .visible(autoDig::get)
        .build()
    );

    private final Setting<Boolean> mine3x3 = sgDigging.add(new BoolSetting.Builder()
        .name("3x3-pickaxe")
        .description("Đào bằng Cúp 3x3 (chỉ cần đào 1 vị trí khối duy nhất phía trước).")
        .defaultValue(true)
        .visible(autoDig::get)
        .build()
    );

    private final Setting<Boolean> autoSwitchTool = sgDigging.add(new BoolSetting.Builder()
        .name("auto-switch-tool")
        .description("Tự động chuyển sang cúp/dụng cụ đào nhanh nhất trong Hotbar.")
        .defaultValue(true)
        .visible(autoDig::get)
        .build()
    );

    // Safety & Auto Log Settings
    private final Setting<Boolean> autoLogSpawner = sgSafety.add(new BoolSetting.Builder()
        .name("auto-log-spawner")
        .description("Tự động ngắt kết nối (out server) ngay lập tức khi phát hiện lồng Spawner.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoLogPlayer = sgSafety.add(new BoolSetting.Builder()
        .name("auto-log-player")
        .description("Tự động ngắt kết nối (out server) ngay lập tức khi phát hiện người chơi (nametag) khác.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgSafety.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Bỏ qua không out nếu người chơi phát hiện nằm trong danh sách bạn bè.")
        .defaultValue(true)
        .visible(autoLogPlayer::get)
        .build()
    );

    private final Long2IntMap chunks = new Long2IntOpenHashMap();
    private Direction lockedDir;
    private float lockedYaw;

    public TunnelBaseFinder() {
        super(Categories.DONUT, "tunnel-base-finder", "Dò tìm hầm (strip mine tunnel). Vừa đi vừa đào thẳng, tự động Out nếu gặp Spawner hoặc Nametag khác.");
    }

    @Override
    public void onActivate() {
        lockedDir = null;
        if (mc.player != null) {
            lockedDir = mc.player.getHorizontalFacing();
            lockedYaw = switch (lockedDir) {
                case SOUTH -> 0f;
                case WEST -> 90f;
                case NORTH -> 180f;
                case EAST -> 270f;
                default -> mc.player.getYaw();
            };
        }
    }

    @Override
    public void onDeactivate() {
        chunks.clear();
        lockedDir = null;
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.attackKey.setPressed(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 1. Kiểm tra an toàn: Nếu có người chơi khác (Nametag) -> Out server
        if (autoLogPlayer.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity player && player != mc.player) {
                    if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;
                    disconnect("Phát hiện người chơi/nametag khác: " + player.getName().getString());
                    return;
                }
            }
        }

        // 2. Vừa đi vừa đào hầm thẳng tuyệt đối
        if (autoDig.get()) {
            if (lockedDir == null) {
                lockedDir = mc.player.getHorizontalFacing();
                lockedYaw = switch (lockedDir) {
                    case SOUTH -> 0f;
                    case WEST -> 90f;
                    case NORTH -> 180f;
                    case EAST -> 270f;
                    default -> mc.player.getYaw();
                };
            }

            // Khóa tâm nhìn (dấu +) cố định 100% thẳng phía trước mượt mà
            if (snapDirection.get()) {
                mc.player.setYaw(lockedYaw);
                mc.player.setPitch(0f);
            }

            BlockPos playerPos = mc.player.getBlockPos();
            BlockPos targetPos = null;
            BlockState targetState = null;

            // Đào ưu tiên khoảng cách 3 khối rồi tới 2 khối phía trước theo đúng hướng thẳng lockedDir
            for (int d = 3; d >= 2; d--) {
                BlockPos headFront = playerPos.offset(lockedDir, d).up();
                BlockPos footFront = playerPos.offset(lockedDir, d);

                BlockState headState = mc.world.getBlockState(headFront);
                BlockState footState = mc.world.getBlockState(footFront);

                if (!headState.isAir() && BlockUtils.canBreak(headFront)) {
                    targetPos = headFront;
                    targetState = headState;
                    break;
                } else if (!mine3x3.get() && !footState.isAir() && BlockUtils.canBreak(footFront)) {
                    targetPos = footFront;
                    targetState = footState;
                    break;
                }
            }

            // Kiểm tra ô khoảng cách 1 phía trước nếu 2-3 đã sạch
            if (targetPos == null) {
                BlockPos head1 = playerPos.offset(lockedDir, 1).up();
                BlockPos foot1 = playerPos.offset(lockedDir, 1);

                BlockState h1State = mc.world.getBlockState(head1);
                BlockState f1State = mc.world.getBlockState(foot1);

                if (!h1State.isAir() && BlockUtils.canBreak(head1)) {
                    targetPos = head1;
                    targetState = h1State;
                } else if (!mine3x3.get() && !f1State.isAir() && BlockUtils.canBreak(foot1)) {
                    targetPos = foot1;
                    targetState = f1State;
                }
            }

            if (targetPos != null && targetState != null) {
                // Tự động chuyển cúp/dụng cụ đào nhanh nhất trong hotbar
                if (autoSwitchTool.get()) {
                    FindItemResult tool = InvUtils.findFastestTool(targetState);
                    if (tool.found() && tool.isHotbar()) {
                        InvUtils.swap(tool.slot(), false);
                    }
                }

                // Khi tốc độ di chuyển > tốc độ đào: Dừng di chuyển & triệt tiêu quán tính ngay lập tức
                mc.options.forwardKey.setPressed(false);
                net.minecraft.util.math.Vec3d vel = mc.player.getVelocity();
                mc.player.setVelocity(0, vel.y, 0);

                BlockUtils.breakBlock(targetPos, true);
                mc.options.attackKey.setPressed(true);
            } else {
                mc.options.attackKey.setPressed(false);
                // Đường hầm phía trước 1-3 khối sạch hoàn toàn -> Mới đi tiếp W
                mc.options.forwardKey.setPressed(true);
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        WorldChunk chunk = event.chunk();

        // 3. Kiểm tra an toàn: Trong Chunk có lồng Spawner -> Out server
        if (autoLogSpawner.get()) {
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity.getType() == BlockEntityType.MOB_SPAWNER || blockEntity instanceof MobSpawnerBlockEntity) {
                    disconnect("Phát hiện lồng Spawner tại X:" + blockEntity.getPos().getX() + " Y:" + blockEntity.getPos().getY() + " Z:" + blockEntity.getPos().getZ());
                    return;
                }
            }
        }

        int tunnelScore = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // Quét các đường hầm ngang (1x2 air corridors)
        for (int y = -60; y < 30; y++) {
            for (int z = 0; z < 16; z++) {
                int airStreak = 0;
                for (int x = 0; x < 16; x++) {
                    pos.set(chunk.getPos().x * 16 + x, y, chunk.getPos().z * 16 + z);
                    BlockState state = chunk.getBlockState(pos);
                    pos.set(chunk.getPos().x * 16 + x, y + 1, chunk.getPos().z * 16 + z);
                    BlockState above = chunk.getBlockState(pos);

                    if (autoLogSpawner.get() && (state.isOf(Blocks.SPAWNER) || above.isOf(Blocks.SPAWNER))) {
                        disconnect("Phát hiện lồng Spawner tại Chunk X:" + (chunk.getPos().x * 16) + " Z:" + (chunk.getPos().z * 16));
                        return;
                    }

                    if (state.isAir() && above.isAir()) airStreak++;
                    else {
                        if (airStreak >= minTunnelBlocks.get()) tunnelScore += airStreak;
                        airStreak = 0;
                    }
                }
                if (airStreak >= minTunnelBlocks.get()) tunnelScore += airStreak;
            }

            // Quét các đường hầm theo hướng Z
            for (int x = 0; x < 16; x++) {
                int airStreak = 0;
                for (int z2 = 0; z2 < 16; z2++) {
                    pos.set(chunk.getPos().x * 16 + x, y, chunk.getPos().z * 16 + z2);
                    BlockState state = chunk.getBlockState(pos);
                    pos.set(chunk.getPos().x * 16 + x, y + 1, chunk.getPos().z * 16 + z2);
                    BlockState above = chunk.getBlockState(pos);

                    if (state.isAir() && above.isAir()) airStreak++;
                    else {
                        if (airStreak >= minTunnelBlocks.get()) tunnelScore += airStreak;
                        airStreak = 0;
                    }
                }
                if (airStreak >= minTunnelBlocks.get()) tunnelScore += airStreak;
            }
        }

        if (tunnelScore > 0) {
            if (chunks.size() > 1000) chunks.clear();
            chunks.put(ChunkPos.toLong(chunk.getPos().x, chunk.getPos().z), tunnelScore);
            info("Tunnel detected at X:%d Z:%d (score: %d)", chunk.getPos().x * 16, chunk.getPos().z * 16, tunnelScore);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Long2IntMap.Entry entry : chunks.long2IntEntrySet()) {
            int cx = (int) entry.getLongKey();
            int cz = (int) (entry.getLongKey() >> 32);
            int ySurface = mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, cx * 16 + 8, cz * 16 + 8);
            double h = ySurface + 0.1;
            event.renderer.box(cx * 16, ySurface, cz * 16, cx * 16 + 16, h, cz * 16 + 16,
                color.get(), color.get(), shapeMode.get(), 0);
        }
    }

    private void disconnect(String reason) {
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.attackKey.setPressed(false);
        }

        if (mc.player != null && mc.player.networkHandler != null) {
            MutableText text = Text.literal("[TunnelBaseFinder] ");
            text.append(Text.literal(reason));

            AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
            if (autoReconnect != null && autoReconnect.isActive()) {
                autoReconnect.toggle();
            }

            mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(text));
            toggle();
        }
    }
}
