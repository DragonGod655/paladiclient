/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.screens.settings;

import paladi.client.gui.GuiTheme;
import paladi.client.gui.screens.settings.base.DynamicRegistryListSettingScreen;
import paladi.client.gui.widgets.WWidget;
import paladi.client.settings.Setting;
import paladi.client.utils.misc.Names;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.Set;

public class EnchantmentListSettingScreen extends DynamicRegistryListSettingScreen<Enchantment> {
    public EnchantmentListSettingScreen(GuiTheme theme, Setting<Set<RegistryKey<Enchantment>>> setting) {
        super(theme, "Select Enchantments", setting, setting.get(), RegistryKeys.ENCHANTMENT);
    }

    @Override
    protected WWidget getValueWidget(RegistryKey<Enchantment> value) {
        return theme.label(Names.get(value));
    }

    @Override
    protected String[] getValueNames(RegistryKey<Enchantment> value) {
        return new String[]{
            Names.get(value),
            value.getValue().toString()
        };
    }
}
