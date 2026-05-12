/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.gui.screens.settings;

import paladi.client.gui.GuiTheme;
import paladi.client.gui.WindowScreen;
import paladi.client.gui.utils.Cell;
import paladi.client.gui.widgets.WLabel;
import paladi.client.gui.widgets.WWidget;
import paladi.client.gui.widgets.containers.WTable;
import paladi.client.gui.widgets.containers.WView;
import paladi.client.gui.widgets.input.WDropdown;
import paladi.client.gui.widgets.input.WTextBox;
import paladi.client.gui.widgets.pressable.WButton;
import paladi.client.renderer.Fonts;
import paladi.client.renderer.text.FontFamily;
import paladi.client.renderer.text.FontInfo;
import paladi.client.settings.FontFaceSetting;
import org.apache.commons.lang3.Strings;

import java.util.List;

public class FontFaceSettingScreen extends WindowScreen {
    private final FontFaceSetting setting;

    private WTable table;

    private WTextBox filter;
    private String filterText = "";

    public FontFaceSettingScreen(GuiTheme theme, FontFaceSetting setting) {
        super(theme, "Select Font");

        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        filter = add(theme.textBox("")).expandX().widget();
        filter.setFocused(true);
        filter.action = () -> {
            filterText = filter.get().trim();

            table.clear();
            initTable();
        };

        window.view.hasScrollBar = false;

        enterAction = () -> {
            List<Cell<?>> row = table.getRow(0);
            if (row == null) return;

            WWidget widget = row.get(2).widget();
            if (widget instanceof WButton button) {
                button.action.run();
            }
        };

        WView view = add(theme.view()).expandX().widget();
        // Prevents double scrolling for view-in-view scenario
        view.maxHeight = window.view.maxHeight - 128;
        view.scrollOnlyWhenMouseOver = false;

        table = view.add(theme.table()).expandX().widget();

        initTable();
    }

    private void initTable() {
        for (FontFamily fontFamily : Fonts.FONT_FAMILIES) {
            String name = fontFamily.getName();

            WLabel item = theme.label(name);
            if (!filterText.isEmpty() && !Strings.CI.contains(name, filterText)) continue;
            table.add(item);

            WDropdown<FontInfo.Type> dropdown = table.add(theme.dropdown(FontInfo.Type.Regular)).right().widget();

            WButton select = table.add(theme.button("Select")).expandCellX().right().widget();
            select.action = () -> {
                setting.set(fontFamily.get(dropdown.get()));
                close();
            };

            table.row();
        }
    }
}
