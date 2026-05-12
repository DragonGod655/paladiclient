/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.accounts.types;

import com.mojang.util.UndashedUuid;
import paladi.client.systems.accounts.Account;
import paladi.client.systems.accounts.AccountType;
import paladi.client.systems.accounts.MicrosoftLogin;
import net.minecraft.client.session.Session;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class MicrosoftAccount extends Account<MicrosoftAccount> {
    private @Nullable String token;
    public MicrosoftAccount(String refreshToken) {
        super(AccountType.Microsoft, refreshToken);
    }

    @Override
    public boolean fetchInfo() {
        token = auth();
        return token != null;
    }

    @Override
    public boolean login() {
        if (token == null) return false;

        super.login();

        setSession(new Session(cache.username, UndashedUuid.fromStringLenient(cache.uuid), token, Optional.empty(), Optional.empty()));
        return true;
    }

    private @Nullable String auth() {
        MicrosoftLogin.LoginData data = MicrosoftLogin.login(name);
        if (!data.isGood()) return null;

        name = data.newRefreshToken;
        cache.username = data.username;
        cache.uuid = data.uuid;

        return data.mcToken;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MicrosoftAccount account)) return false;
        return account.name.equals(this.name);
    }
}
