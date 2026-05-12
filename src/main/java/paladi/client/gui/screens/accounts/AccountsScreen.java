/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.screens.accounts;

import paladi.client.gui.GuiTheme;
import paladi.client.gui.WindowScreen;
import paladi.client.gui.widgets.WAccount;
import paladi.client.gui.widgets.containers.WContainer;
import paladi.client.gui.widgets.containers.WHorizontalList;
import paladi.client.gui.widgets.pressable.WButton;
import paladi.client.systems.accounts.Account;
import paladi.client.systems.accounts.AccountType;
import paladi.client.systems.accounts.Accounts;
import paladi.client.utils.misc.NbtUtils;
import paladi.client.utils.network.PaladiExecutor;
import org.jetbrains.annotations.Nullable;

import static paladi.client.PaladiClient.mc;

public class AccountsScreen extends WindowScreen {
    public AccountsScreen(GuiTheme theme) {
        super(theme, "Accounts");
    }

    @Override
    public void initWidgets() {
        // Accounts
        for (Account<?> account : Accounts.get()) {
            WAccount wAccount = add(theme.account(this, account)).expandX().widget();
            wAccount.refreshScreenAction = this::reload;
        }

        // Add account
        WHorizontalList l = add(theme.horizontalList()).expandX().widget();

        addButton(l, "Cracked", () -> mc.setScreen(new AddCrackedAccountScreen(theme, this)));
        addButton(l, "Altening", () -> mc.setScreen(new AddAlteningAccountScreen(theme, this)));
        addButton(l, "Session", () -> mc.setScreen(new AddSessionAccountScreen(theme, this)));
        addButton(l, "Microsoft", () -> mc.setScreen(new AddMicrosoftAccountScreen(theme, this)));
    }

    private void addButton(WContainer c, String text, Runnable action) {
        WButton button = c.add(theme.button(text)).expandX().widget();
        button.action = action;
    }

    public static void addAccount(@Nullable AddAccountScreen screen, AccountsScreen parent, Account<?> account) {
        if (screen != null) screen.locked = true;

        PaladiExecutor.execute(() -> {
            if (!account.fetchInfo()) {
                mc.execute(() -> {
                    if (screen != null) screen.locked = false;
                });
                return;
            }

            Accounts.get().add(account);

            if (account.login()) {
                if (account.getType() != AccountType.Cracked) account.getCache().loadHead(parent::reload);
                Accounts.get().save();
            }

            mc.execute(() -> {
                if (screen != null) {
                    screen.locked = false;
                    screen.close();
                }

                parent.reload();
            });
        });
    }

    @Override
    public boolean toClipboard() {
        return NbtUtils.toClipboard(Accounts.get());
    }

    @Override
    public boolean fromClipboard() {
        return NbtUtils.fromClipboard(Accounts.get());
    }
}
