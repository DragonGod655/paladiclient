/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.mixin;

import com.mojang.authlib.Environment;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.net.Proxy;

@Mixin(YggdrasilMinecraftSessionService.class)
public interface YggdrasilMinecraftSessionServiceAccessor {
    @Invoker("<init>")
    static YggdrasilMinecraftSessionService Paladi$createYggdrasilMinecraftSessionService(final ServicesKeySet servicesKeySet, final Proxy proxy, final Environment env) {
        throw new UnsupportedOperationException();
    }
}
