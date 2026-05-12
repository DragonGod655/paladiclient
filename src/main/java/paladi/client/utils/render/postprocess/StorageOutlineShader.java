package paladi.client.utils.render.postprocess;

import paladi.client.renderer.MeshRenderer;
import paladi.client.renderer.PaladiRenderPipelines;
import paladi.client.systems.modules.Modules;
import paladi.client.systems.modules.render.StorageESP;

public class StorageOutlineShader extends PostProcessShader {
    private static StorageESP storageESP;

    public StorageOutlineShader() {
        super(PaladiRenderPipelines.POST_OUTLINE);
    }

    @Override
    protected boolean shouldDraw() {
        if (storageESP == null) storageESP = Modules.get().get(StorageESP.class);
        return storageESP.isShader();
    }

    @Override
    protected void setupPass(MeshRenderer renderer) {
        renderer.uniform("OutlineData", OutlineUniforms.write(
            storageESP.outlineWidth.get(),
            storageESP.fillOpacity.get() / 255.0f,
            storageESP.shapeMode.get().ordinal(),
            storageESP.glowMultiplier.get().floatValue()
        ));
    }
}
