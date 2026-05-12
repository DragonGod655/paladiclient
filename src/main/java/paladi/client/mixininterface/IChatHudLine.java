/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixininterface;

import com.mojang.authlib.GameProfile;

public interface IChatHudLine {
    String Paladi$getText();

    int Paladi$getId();

    void Paladi$setId(int id);

    GameProfile Paladi$getSender();

    void Paladi$setSender(GameProfile profile);
}
