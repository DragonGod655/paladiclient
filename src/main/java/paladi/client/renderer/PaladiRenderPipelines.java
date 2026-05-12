/*
 * This file is part of the Paladi Client distribution (https://github.com/meteordevelopment/paladi-client).
 * Copyright (c) Paladi Development.
 */

package paladi.client.renderer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import paladi.client.PaladiClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceManager;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public abstract class PaladiRenderPipelines {
    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();

    // Snippets

    private static final RenderPipeline.Snippet MESH_UNIFORMS = RenderPipeline.builder()
        .withUniform("MeshData", UniformType.UNIFORM_BUFFER)
        .buildSnippet();

    // World

    public static final RenderPipeline WORLD_COLORED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(PaladiClient.identifier("pipeline/world_colored"))
        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(PaladiClient.identifier("shaders/pos_color.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/pos_color.frag"))
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build()
    );

    public static final RenderPipeline WORLD_COLORED_LINES = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLineSmooth()
        .withLocation(PaladiClient.identifier("pipeline/world_colored_lines"))
        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
        .withVertexShader(PaladiClient.identifier("shaders/pos_color.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/pos_color.frag"))
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build()
    );

    public static final RenderPipeline WORLD_COLORED_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(PaladiClient.identifier("pipeline/world_colored_depth"))
        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(PaladiClient.identifier("shaders/pos_color.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/pos_color.frag"))
        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build()
    );

    public static final RenderPipeline WORLD_COLORED_LINES_DEPTH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLineSmooth()
        .withLocation(PaladiClient.identifier("pipeline/world_colored_lines_depth"))
        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
        .withVertexShader(PaladiClient.identifier("shaders/pos_color.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/pos_color.frag"))
        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build()
    );

    // UI

    public static final RenderPipeline UI_COLORED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(PaladiClient.identifier("pipeline/ui_colored"))
        .withVertexFormat(PaladiVertexFormats.POS2_COLOR, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(PaladiClient.identifier("shaders/pos_color.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/pos_color.frag"))
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(true)
        .build()
    );

    public static final RenderPipeline UI_COLORED_LINES = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(PaladiClient.identifier("pipeline/ui_colored_lines"))
        .withVertexFormat(PaladiVertexFormats.POS2_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
        .withVertexShader(PaladiClient.identifier("shaders/pos_color.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/pos_color.frag"))
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(true)
        .build()
    );

    public static final RenderPipeline UI_TEXTURED = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(PaladiClient.identifier("pipeline/ui_textured"))
        .withVertexFormat(PaladiVertexFormats.POS2_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(PaladiClient.identifier("shaders/pos_tex_color.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/pos_tex_color.frag"))
        .withSampler("u_Texture")
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(true)
        .build()
    );

    public static final RenderPipeline UI_TEXT = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(PaladiClient.identifier("pipeline/ui_text"))
        .withVertexFormat(PaladiVertexFormats.POS2_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(PaladiClient.identifier("shaders/text.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/text.frag"))
        .withSampler("u_Texture")
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(true)
        .build()
    );

    // Post Process

    public static final RenderPipeline POST_OUTLINE = add(new ExtendedRenderPipelineBuilder()
        .withLocation(PaladiClient.identifier("pipeline/post/outline"))
        .withVertexFormat(PaladiVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(PaladiClient.identifier("shaders/post-process/base.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/post-process/outline.frag"))
        .withSampler("u_Texture")
        .withUniform("PostData", UniformType.UNIFORM_BUFFER)
        .withUniform("OutlineData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build()
    );

    public static final RenderPipeline POST_IMAGE = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(PaladiClient.identifier("pipeline/post/image"))
        .withVertexFormat(PaladiVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(PaladiClient.identifier("shaders/post-process/base.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/post-process/image.frag"))
        .withSampler("u_Texture")
        .withSampler("u_TextureI")
        .withUniform("PostData", UniformType.UNIFORM_BUFFER)
        .withUniform("ImageData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build()
    );

    // Blur

    public static final RenderPipeline BLUR_DOWN = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(PaladiClient.identifier("pipeline/blur/down"))
        .withVertexFormat(PaladiVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(PaladiClient.identifier("shaders/blur.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/blur_down.frag"))
        .withSampler("u_Texture")
        .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build()
    );

    public static final RenderPipeline BLUR_UP = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(PaladiClient.identifier("pipeline/blur/up"))
        .withVertexFormat(PaladiVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(PaladiClient.identifier("shaders/blur.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/blur_up.frag"))
        .withSampler("u_Texture")
        .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build()
    );

    public static final RenderPipeline BLUR_PASSTHROUGH = add(new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
        .withLocation(PaladiClient.identifier("pipeline/blur/up"))
        .withVertexFormat(PaladiVertexFormats.POS2, VertexFormat.DrawMode.TRIANGLES)
        .withVertexShader(PaladiClient.identifier("shaders/passthrough.vert"))
        .withFragmentShader(PaladiClient.identifier("shaders/passthrough.frag"))
        .withSampler("u_Texture")
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build()
    );

    private static RenderPipeline add(RenderPipeline pipeline) {
        PIPELINES.add(pipeline);
        return pipeline;
    }

    public static void precompile() {
        GpuDevice device = RenderSystem.getDevice();
        ResourceManager resources = MinecraftClient.getInstance().getResourceManager();

        for (RenderPipeline pipeline : PIPELINES) {
            device.precompilePipeline(pipeline, (identifier, shaderType) -> {
                var resource = resources.getResource(identifier).get();

                try (var in = resource.getInputStream()) {
                    return IOUtils.toString(in, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private PaladiRenderPipelines() {}
}
