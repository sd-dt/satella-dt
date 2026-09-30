package greenebolt.autotrade;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * 附魔光效渲染层（26.2 官方名版本）。
 *
 * <p>每种「形态」（{@link GlintShape}）× 每种「颜色」（{@link GlintPreset}）组合成一套独立的
 * {@link RenderType}，纹理路径为 {@code satella:textures/misc/<形态>/glint_<颜色>.png}。
 *
 * <p>形态的差别只在纹理本身的 alpha：
 * <ul>
 *   <li>{@code cool/}：暗处 alpha=0，客户端 {@code glint.fsh} 的 {@code if (color.a < 0.1) discard;}
 *       会丢弃这些像素，光效只保留在亮网格线上（消除材质包眼睛部位的亚像素闪烁）。</li>
 *   <li>{@code default/}：alpha 全不透明，保持原来的整体光膜效果。</li>
 * </ul>
 * 因此形态切换只是换纹理，无需改动管线、纹理变换或深度分层 —— 与 1.21.11 主版
 * （{@code GlintRenderLayer}）的行为一致。
 */
public final class GlintRenderType {
    private static final Map<GlintShape, Map<GlintPreset, RenderType>> GLINT = shapeMap();
    private static final Map<GlintShape, Map<GlintPreset, RenderType>> TRANSLUCENT = shapeMap();
    private static final Map<GlintShape, Map<GlintPreset, RenderType>> ARMOR_GLINT = shapeMap();

    private static Map<GlintShape, Map<GlintPreset, RenderType>> shapeMap() {
        Map<GlintShape, Map<GlintPreset, RenderType>> map = new EnumMap<>(GlintShape.class);
        for (GlintShape shape : GlintShape.values()) {
            map.put(shape, new EnumMap<>(GlintPreset.class));
        }
        return map;
    }

    static {
        for (GlintShape shape : GlintShape.values()) {
            for (GlintPreset preset : GlintPreset.values()) {
                String name = shape.getStringValue() + "_" + preset.getStringValue();
                GLINT.get(shape).put(preset,
                        build("glint_" + name, shape, preset, TextureTransform.GLINT_TEXTURING, null));
                TRANSLUCENT.get(shape).put(preset,
                        build("glint_translucent_" + name, shape, preset, TextureTransform.GLINT_TEXTURING,
                                OutputTarget.ITEM_ENTITY_TARGET));
                ARMOR_GLINT.get(shape).put(preset,
                        build("armor_glint_" + name, shape, preset,
                                TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING, null));
            }
        }
    }

    private GlintRenderType() {}

    public static RenderType glint() { return selected(GLINT, RenderTypes.glint()); }
    public static RenderType translucent() { return selected(TRANSLUCENT, RenderTypes.glintTranslucent()); }
    public static RenderType armorGlint() { return selected(ARMOR_GLINT, RenderTypes.armorEntityGlint()); }

    private static RenderType selected(Map<GlintShape, Map<GlintPreset, RenderType>> byShape, RenderType fallback) {
        Map<GlintPreset, RenderType> layers = byShape.get(currentShape());
        if (layers == null) {
            return fallback;
        }
        GlintPreset preset = (GlintPreset) AutoTradeConfigs.Trade.ENCHANTMENT_COLOR.getOptionListValue();
        return layers.getOrDefault(preset, fallback);
    }

    /** 读取当前形态；配置尚未就绪时退回「炫酷」，避免渲染路径抛异常。 */
    private static GlintShape currentShape() {
        try {
            Object value = AutoTradeConfigs.Trade.ENCHANTMENT_SHAPE.getOptionListValue();
            if (value instanceof GlintShape shape) {
                return shape;
            }
        } catch (Throwable ignored) {
            // 配置未初始化：使用默认形态
        }
        return GlintShape.COOL;
    }

    private static RenderType build(String name, GlintShape shape, GlintPreset preset,
                                    TextureTransform transform, OutputTarget target) {
        Identifier texture = Identifier.fromNamespaceAndPath("satella",
                "textures/misc/" + shape.textureFolder() + "/" + preset.textureName() + ".png");
        RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(RenderPipelines.GLINT)
                .withTexture("Sampler0", texture)
                .setTextureTransform(transform);
        if (transform == TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING) {
            builder.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
        }
        if (target != null) {
            builder.setOutputTarget(target);
        }
        return greenebolt.autotrade.mixin.RenderTypeInvoker.autoTrade$create(name, builder.createRenderSetup());
    }
}
