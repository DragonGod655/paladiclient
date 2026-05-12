/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.themes.Paladi.widgets;

import paladi.client.gui.WidgetScreen;
import paladi.client.gui.themes.Paladi.PaladiWidget;
import paladi.client.gui.widgets.WAccount;
import paladi.client.systems.accounts.Account;
import paladi.client.utils.render.color.Color;

public class WPaladiAccount extends WAccount implements PaladiWidget {
    public WPaladiAccount(WidgetScreen screen, Account<?> account) {
        super(screen, account);
    }

    @Override
    protected Color loggedInColor() {
        return theme().loggedInColor.get();
    }

    @Override
    protected Color accountTypeColor() {
        return theme().textSecondaryColor.get();
    }
}
