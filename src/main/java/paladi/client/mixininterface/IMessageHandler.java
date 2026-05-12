/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixininterface;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;

public interface IMessageHandler {
    /** Only valid inside of {@link net.minecraft.client.gui.hud.ChatHud#addMessage(Text, MessageSignatureData, MessageIndicator)} call */
    GameProfile Paladi$getSender();
}
