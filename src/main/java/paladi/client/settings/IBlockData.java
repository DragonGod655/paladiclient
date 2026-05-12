/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.settings;

import paladi.client.gui.GuiTheme;
import paladi.client.gui.WidgetScreen;
import paladi.client.utils.misc.IChangeable;
import paladi.client.utils.misc.ICopyable;
import paladi.client.utils.misc.ISerializable;
import net.minecraft.block.Block;

public interface IBlockData<T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>> {
    WidgetScreen createScreen(GuiTheme theme, Block block, BlockDataSetting<T> setting);
}
