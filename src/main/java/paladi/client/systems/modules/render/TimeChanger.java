/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.render;

import paladi.client.events.packets.PacketEvent;
import paladi.client.events.world.TickEvent;
import paladi.client.settings.DoubleSetting;
import paladi.client.settings.Setting;
import paladi.client.settings.SettingGroup;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;

public class TimeChanger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> time = sgGeneral.add(new DoubleSetting.Builder()
        .name("time")
        .description("The specified time to be set.")
        .defaultValue(0)
        .sliderRange(-20000, 20000)
        .build()
    );

    long oldTime;

    public TimeChanger() {
        super(Categories.Render, "time-changer", "Makes you able to set a custom time.");
    }

    @Override
    public void onActivate() {
        oldTime = mc.world.getTime();
    }

    @Override
    public void onDeactivate() {
        mc.world.getLevelProperties().setTimeOfDay(oldTime);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof WorldTimeUpdateS2CPacket) {
            oldTime = ((WorldTimeUpdateS2CPacket) event.packet).timeOfDay();
            event.cancel();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        mc.world.getLevelProperties().setTimeOfDay(time.get().longValue());
    }
}
