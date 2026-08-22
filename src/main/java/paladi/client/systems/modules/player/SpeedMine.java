/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.player;

import paladi.client.events.packets.PacketEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.mixin.ClientPlayerInteractionManagerAccessor;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static net.minecraft.entity.effect.StatusEffects.HASTE;

public class SpeedMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHaste = settings.createGroup("Haste Potion Spoof");

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .defaultValue(Mode.Damage)
        .onChanged(mode -> removeHaste())
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Selected blocks.")
        .filter(block -> block.getHardness() > 0)
        .visible(() -> mode.get() != Mode.Haste)
        .build()
    );

    private final Setting<ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("blocks-filter")
        .description("How to use the blocks setting.")
        .defaultValue(ListMode.Blacklist)
        .visible(() -> mode.get() != Mode.Haste)
        .build()
    );

    public final Setting<Double> modifier = sgGeneral.add(new DoubleSetting.Builder()
        .name("modifier")
        .description("Mining speed modifier. An additional value of 0.2 is equivalent to one haste level (1.2 = haste 1).")
        .defaultValue(1.4)
        .visible(() -> mode.get() == Mode.Normal)
        .min(0)
        .build()
    );

    // Haste Potion Spoof Settings
    public final Setting<Boolean> hasteSpoof = sgHaste.add(new BoolSetting.Builder()
        .name("haste-spoof")
        .description("Spoofs client-side Haste potion effect for fast mining.")
        .defaultValue(true)
        .onChanged(b -> { if (!b) removeHaste(); })
        .build()
    );

    public final Setting<Integer> hasteAmplifier = sgHaste.add(new IntSetting.Builder()
        .name("haste-level")
        .description("The Haste amplifier level (1 = Haste I, 2 = Haste II, etc.).")
        .defaultValue(2)
        .min(1)
        .max(255)
        .sliderMax(10)
        .visible(hasteSpoof::get)
        .onChanged(i -> removeHaste())
        .build()
    );

    public final Setting<Boolean> showIcon = sgHaste.add(new BoolSetting.Builder()
        .name("show-icon")
        .description("Displays the Haste potion icon on your HUD like a real potion.")
        .defaultValue(true)
        .visible(hasteSpoof::get)
        .onChanged(b -> removeHaste())
        .build()
    );

    public final Setting<Boolean> showParticles = sgHaste.add(new BoolSetting.Builder()
        .name("show-particles")
        .description("Shows potion particle swirls around your player.")
        .defaultValue(false)
        .visible(hasteSpoof::get)
        .onChanged(b -> removeHaste())
        .build()
    );

    private final Setting<Boolean> instamine = sgGeneral.add(new BoolSetting.Builder()
        .name("instamine")
        .description("Whether or not to instantly mine blocks under certain conditions.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Damage)
        .build()
    );

    private final Setting<Boolean> grimBypass = sgGeneral.add(new BoolSetting.Builder()
        .name("grim-bypass")
        .description("Bypasses Grim's fastbreak check.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Damage)
        .build()
    );

    public final Setting<Double> progressLimit = sgGeneral.add(new DoubleSetting.Builder()
        .name("progress-limit")
        .description("The breaking progress threshold to instantly break the block.")
        .defaultValue(0.9)
        .min(0.5)
        .max(1.0)
        .visible(() -> mode.get() == Mode.Damage)
        .build()
    );

    public final Setting<Double> instamineLimit = sgGeneral.add(new DoubleSetting.Builder()
        .name("instamine-limit")
        .description("The breaking delta threshold to instantly break blocks. Lower values instamine harder blocks.")
        .defaultValue(0.5)
        .visible(() -> mode.get() == Mode.Damage && instamine.get())
        .min(0.1)
        .max(1.0)
        .build()
    );

    public SpeedMine() {
        super(Categories.Player, "speed-mine", "Allows you to quickly mine blocks with Haste potion spoofing.");
    }

    @Override
    public void onDeactivate() {
        removeHaste();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        if (mode.get() == Mode.Haste || hasteSpoof.get()) {
            StatusEffectInstance haste = mc.player.getStatusEffect(HASTE);
            int targetAmp = hasteAmplifier.get() - 1;

            if (haste == null || haste.getAmplifier() != targetAmp) {
                mc.player.setStatusEffect(new StatusEffectInstance(HASTE, StatusEffectInstance.INFINITE, targetAmp, false, showParticles.get(), showIcon.get()), null);
            }
        } else {
            removeHaste();
        }

        if (mode.get() == Mode.Damage) {
            ClientPlayerInteractionManagerAccessor im = (ClientPlayerInteractionManagerAccessor) mc.interactionManager;
            float progress = im.Paladi$getBreakingProgress();
            BlockPos pos = im.Paladi$getCurrentBreakingBlockPos();

            if (pos == null || progress <= 0) return;
            if (progress + mc.world.getBlockState(pos).calcBlockBreakingDelta(mc.player, mc.world, pos) >= progressLimit.get().floatValue())
                im.Paladi$setCurrentBreakingProgress(1f);
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Send event) {
        if (!(mode.get() == Mode.Damage) || !grimBypass.get()) return;

        if (event.packet instanceof PlayerActionC2SPacket packet && packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, packet.getPos().up(), packet.getDirection()));
        }
    }

    private void removeHaste() {
        if (!Utils.canUpdate()) return;

        StatusEffectInstance haste = mc.player.getStatusEffect(HASTE);
        if (haste != null) mc.player.removeStatusEffect(HASTE);
    }

    public boolean filter(Block block) {
        if (blocksFilter.get() == ListMode.Blacklist && !blocks.get().contains(block)) return true;
        return blocksFilter.get() == ListMode.Whitelist && blocks.get().contains(block);
    }

    public boolean instamine() {
        return isActive() && mode.get() == Mode.Damage && instamine.get();
    }

    public enum Mode {
        Normal,
        Haste,
        Damage
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }
}
