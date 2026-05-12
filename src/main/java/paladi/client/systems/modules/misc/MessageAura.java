/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.modules.misc;

import paladi.client.events.entity.EntityAddedEvent;
import paladi.client.settings.BoolSetting;
import paladi.client.settings.Setting;
import paladi.client.settings.SettingGroup;
import paladi.client.settings.StringSetting;
import paladi.client.systems.friends.Friends;
import paladi.client.systems.modules.Categories;
import paladi.client.systems.modules.Module;
import paladi.client.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

public class MessageAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> message = sgGeneral.add(new StringSetting.Builder()
        .name("message")
        .description("The specified message sent to the player.")
        .defaultValue("Paladi on Crack!")
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Will not send any messages to people friended.")
        .defaultValue(false)
        .build()
    );

    public MessageAura() {
        super(Categories.Misc, "message-aura", "Sends a specified message to any player that enters render distance.");
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!(event.entity instanceof PlayerEntity) || event.entity.getUuid().equals(mc.player.getUuid())) return;

        if (!ignoreFriends.get() || (ignoreFriends.get() && !Friends.get().isFriend((PlayerEntity)event.entity))) {
            ChatUtils.sendPlayerMsg("/msg " + event.entity.getName().getString() + " " + message.get());
        }
    }
}
