/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.utils.tooltip;

import net.minecraft.client.gui.tooltip.OrderedTextTooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public class TextTooltipComponent extends OrderedTextTooltipComponent implements PaladiTooltipData {
    public TextTooltipComponent(OrderedText text) {
        super(text);
    }

    public TextTooltipComponent(Text text) {
        this(text.asOrderedText());
    }

    @Override
    public TooltipComponent getComponent() {
        return this;
    }
}
