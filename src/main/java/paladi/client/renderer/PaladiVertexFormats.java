/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.renderer;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

public abstract class PaladiVertexFormats {
    public static final VertexFormat POS2 = VertexFormat.builder()
        .add("Position", PaladiVertexFormatElements.POS2)
        .build();

    public static final VertexFormat POS2_COLOR = VertexFormat.builder()
        .add("Position", PaladiVertexFormatElements.POS2)
        .add("Color", VertexFormatElement.COLOR)
        .build();

    public static final VertexFormat POS2_TEXTURE_COLOR = VertexFormat.builder()
        .add("Position", PaladiVertexFormatElements.POS2)
        .add("Texture", VertexFormatElement.UV)
        .add("Color", VertexFormatElement.COLOR)
        .build();

    private PaladiVertexFormats() {}
}
