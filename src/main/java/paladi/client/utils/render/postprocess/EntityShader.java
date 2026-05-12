package paladi.client.utils.render.postprocess;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import paladi.client.mixininterface.IWorldRenderer;
import paladi.client.utils.render.CustomOutlineVertexConsumerProvider;
import net.minecraft.entity.Entity;

import static paladi.client.PaladiClient.mc;

public abstract class EntityShader extends PostProcessShader {
    public final CustomOutlineVertexConsumerProvider vertexConsumerProvider;

    protected EntityShader(RenderPipeline pipeline) {
        super(pipeline);
        this.vertexConsumerProvider = new CustomOutlineVertexConsumerProvider();
    }

    public abstract boolean shouldDraw(Entity entity);

    @Override
    protected void preDraw() {
        ((IWorldRenderer) mc.worldRenderer).Paladi$pushEntityOutlineFramebuffer(framebuffer);
    }

    @Override
    protected void postDraw() {
        ((IWorldRenderer) mc.worldRenderer).Paladi$popEntityOutlineFramebuffer();
    }

    public void submitVertices() {
        submitVertices(vertexConsumerProvider::draw);
    }
}
