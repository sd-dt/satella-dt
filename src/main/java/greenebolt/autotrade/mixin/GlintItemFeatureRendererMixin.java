package greenebolt.autotrade.mixin;

import greenebolt.autotrade.GlintRenderType;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 26.3 的物品光效不再走 {@code getFoilBuffer()}，而是在 {@code prepareMainSubmit()}
 * 里按 {@code Submit.foilType()} 从 {@code BakedQuad.MaterialInfo} 取现成的
 * {@code itemGlintRenderType()}（模型烘焙时按图集生成）。这里在取用点做重定向：
 * 保留原版 Sampler0（图集贴图），只把 GlintSampler 换成配置颜色的自定义贴图。
 */
@Mixin(value = ItemFeatureRenderer.class, remap = false)
public class GlintItemFeatureRendererMixin {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("satella");
    private static boolean logged;

    @Redirect(method = "prepareMainSubmit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemGlintRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"),
            remap = false)
    private static RenderType autoTrade$glint(BakedQuad.MaterialInfo material) {
        if (!logged) {
            logged = true;
            LOGGER.info("26.3 ItemFeatureRenderer glint redirect active | 附魔光效状态：{} | 光效贴图来源包：{}",
                    greenebolt.autotrade.GlintCompat.describe(),
                    greenebolt.autotrade.GlintCompat.glintSources());
        }
        boolean translucent = material.layer() != null && material.layer().translucent();
        return GlintRenderType.forItem(material.sprite().atlasLocation(), translucent);
    }
}
