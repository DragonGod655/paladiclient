/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import com.mojang.authlib.GameProfile;
import paladi.client.mixininterface.IChatHudLineVisible;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChatHudLine.Visible.class)
public abstract class ChatHudLineVisibleMixin implements IChatHudLineVisible {
    @Shadow @Final private OrderedText content;
    @Unique private int id;
    @Unique private GameProfile sender;
    @Unique private boolean startOfEntry;

    @Override
    public String Paladi$getText() {
        StringBuilder sb = new StringBuilder();

        content.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });

        return sb.toString();
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

    @Override
    public boolean Paladi$isStartOfEntry() {
        return startOfEntry;
    }

    @Override
    public void Paladi$setStartOfEntry(boolean start) {
        startOfEntry = start;
    }
}
