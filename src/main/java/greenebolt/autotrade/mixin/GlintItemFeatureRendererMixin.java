package greenebolt.autotrade.mixin;

import greenebolt.autotrade.GlintRenderType;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemFeatureRenderer.class, remap = false)
public class GlintItemFeatureRendererMixin {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("satella");
    private static boolean logged;
    @Redirect(method = "getFoilBuffer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;glint()Lnet/minecraft/client/renderer/rendertype/RenderType;"), remap = false)
    private static RenderType autoTrade$glint() {
        if (!logged) {
            logged = true;
            LOGGER.info("26.2 ItemFeatureRenderer glint redirect active");
        }
        return GlintRenderType.glint();
    }

    @Redirect(method = "getFoilBuffer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;glintTranslucent()Lnet/minecraft/client/renderer/rendertype/RenderType;"), remap = false)
    private static RenderType autoTrade$translucent() { return GlintRenderType.translucent(); }
}
