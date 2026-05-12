package paladi.client.systems.modules.krypton;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Module;
import paladi.client.systems.modules.Categories;
import paladi.client.utils.player.FindItemResult;
import paladi.client.utils.player.InvUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.orbit.EventHandler;

import java.util.Comparator;

import static paladi.client.PaladiClient.mc;

public class KryptonAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Range to search for players.")
        .defaultValue(10.0)
        .min(1)
        .sliderMax(15)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range to place crystals.")
        .defaultValue(5.0)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Delay between placing crystals (ticks).")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> breakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("break-delay")
        .description("Delay between breaking crystals (ticks).")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private int placeTimer, breakTimer;

    public KryptonAura() {
        super(Categories.COMBAT, "krypton-aura", "High-performance Crystal Aura for DonutSMP.");
    }

    @Override
    public void onActivate() {
        placeTimer = 0;
        breakTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Find Target
        PlayerEntity target = mc.world.getEntitiesByClass(PlayerEntity.class, mc.player.getBoundingBox().expand(targetRange.get()), p -> p != mc.player && p.isAlive())
            .stream()
            .min(Comparator.comparingDouble(p -> mc.player.distanceTo(p)))
            .orElse(null);

        if (target == null) return;

        // Break logic
        if (breakTimer > 0) breakTimer--;
        else {
            EndCrystalEntity crystal = mc.world.getEntitiesByClass(EndCrystalEntity.class, mc.player.getBoundingBox().expand(placeRange.get()), c -> c.isAlive())
                .stream()
                .min(Comparator.comparingDouble(c -> target.distanceTo(c)))
                .orElse(null);

            if (crystal != null) {
                mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
                mc.player.swingHand(Hand.MAIN_HAND);
                breakTimer = breakDelay.get();
            }
        }

        // Place logic
        if (placeTimer > 0) placeTimer--;
        else {
            FindItemResult crystalItem = InvUtils.findInHotbar(Items.END_CRYSTAL);
            if (!crystalItem.found()) return;

            BlockPos bestPos = null;
            double bestDist = Double.MAX_VALUE;

            for (int x = -6; x <= 6; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -6; z <= 6; z++) {
                        BlockPos pos = mc.player.getBlockPos().add(x, y, z);
                        if (isValidPlace(pos)) {
                            double dist = target.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                            if (dist < bestDist) {
                                bestDist = dist;
                                bestPos = pos;
                            }
                        }
                    }
                }
            }

            if (bestPos != null) {
                InvUtils.swap(crystalItem.slot(), true);
                
                BlockHitResult hitResult = new BlockHitResult(new Vec3d(bestPos.getX() + 0.5, bestPos.getY() + 0.5, bestPos.getZ() + 0.5), Direction.UP, bestPos, false);
                mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0));
                
                InvUtils.swapBack();
                placeTimer = placeDelay.get();
            }
        }
    }

    private boolean isValidPlace(BlockPos pos) {
        if (mc.world.getBlockState(pos).getBlock() != Blocks.OBSIDIAN && mc.world.getBlockState(pos).getBlock() != Blocks.BEDROCK) return false;
        
        BlockPos up1 = pos.up();
        if (!mc.world.getBlockState(up1).isAir()) return false;
        
        BlockPos up2 = pos.up(2);
        if (!mc.world.getBlockState(up2).isAir()) return false;

        return mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= placeRange.get() * placeRange.get();
    }
}
