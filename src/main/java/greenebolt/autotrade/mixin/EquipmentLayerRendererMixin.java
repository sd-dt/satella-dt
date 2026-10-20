package greenebolt.autotrade.mixin;

import greenebolt.autotrade.GlintRenderType;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 26.3 的盔甲光效改为 {@code RenderTypes.armorCutoutNoCullGlint(layerTexture)}：
 * Sampler0 = 盔甲层贴图，GlintSampler = 原版盔甲光效。重定向后只替换 GlintSampler，
 * 管线 / {@code ARMOR_ENTITY_GLINT_TEXTURING} 变换 / {@code VIEW_OFFSET_Z_LAYERING} 分层照抄原版。
 */
@Mixin(value = EquipmentLayerRenderer.class, remap = false)
public class EquipmentLayerRendererMixin {
    @Redirect(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"),
            remap = false)
    private static RenderType autoTrade$armorGlint(Identifier layerTexture) {
        return GlintRenderType.forArmor(layerTexture);
    }
}
