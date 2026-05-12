/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import com.mojang.authlib.GameProfile;
import paladi.client.mixininterface.IChatHudLine;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ChatHudLine.class)
public abstract class ChatHudLineMixin implements IChatHudLine {
    @Shadow @Final private Text content;
    @Unique private int id;
    @Unique private GameProfile sender;

    @Override
    public String Paladi$getText() {
        return content.getString();
    }

    @Override
    public int Paladi$getId() {
        return id;
    }

    @Override
    public void Paladi$setId(int id) {
        this.id = id;
    }

    @Override
    public GameProfile Paladi$getSender() {
        return sender;
    }

    @Override
    public void Paladi$setSender(GameProfile profile) {
        sender = profile;
    }
}
