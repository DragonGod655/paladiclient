/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.systems.accounts.types;

import paladi.client.systems.accounts.Account;
import paladi.client.systems.accounts.AccountType;
import net.minecraft.client.session.Session;
import net.minecraft.util.Uuids;

import java.util.Optional;

public class CrackedAccount extends Account<CrackedAccount> {
    public CrackedAccount(String name) {
        super(AccountType.Cracked, name);
    }

    @Override
    public boolean fetchInfo() {
        cache.username = name;
        return true;
    }

    @Override
    public boolean login() {
        super.login();

        setSession(new Session(name, Uuids.getOfflinePlayerUuid(name), "", Optional.empty(), Optional.empty()));
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CrackedAccount account)) return false;
        return account.getUsername().equals(this.getUsername());
    }
}
