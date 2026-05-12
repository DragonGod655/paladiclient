/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.misc;

import paladi.client.events.world.TickEvent;
import paladi.client.settings.*;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.item.ItemStack;

public class DurabilityAlert extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> threshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("threshold")
        .description("The durability percentage threshold to alert at.")
        .defaultValue(10)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> alertArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("armor")
        .description("Alert for armor.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> alertTools = sgGeneral.add(new BoolSetting.Builder()
        .name("tools")
        .description("Alert for held tools.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Plays a sound when durability is low.")
        .defaultValue(true)
        .build()
    );

    private boolean alertedArmor = false;
    private boolean alertedTools = false;

    public DurabilityAlert() {
        super(Categories.Misc, "durability-alert", "Alerts you when your armor or tools are low on durability.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        boolean lowArmor = false;
        if (alertArmor.get()) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (isLow(stack)) {
                    lowArmor = true;
                    break;
                }
            }
        }

        boolean lowTools = false;
        if (alertTools.get()) {
            if (isLow(mc.player.getMainHandStack()) || isLow(mc.player.getOffHandStack())) {
                lowTools = true;
            }
        }

        if (lowArmor && !alertedArmor) {
            alert("Your armor is low on durability!");
            alertedArmor = true;
        } else if (!lowArmor) {
            alertedArmor = false;
        }

        if (lowTools && !alertedTools) {
            alert("Your tool is low on durability!");
            alertedTools = true;
        } else if (!lowTools) {
            alertedTools = false;
        }
    }

    private boolean isLow(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) return false;
        double pct = ((double) (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage()) * 100.0;
        return pct <= threshold.get();
    }

    private void alert(String message) {
        ChatUtils.info(message);
        if (playSound.get()) {
            mc.world.playSound(mc.player, mc.player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1f, 1f);
        }
    }
}
