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
 *
 * <p><b>不接管的情形</b>（直接返回原版 {@code RenderType}，不创建任何自定义渲染层）：
 * <ol>
 *   <li>「附魔显示颜色」= {@link GlintPreset#NONE}（原版光效，默认值）；</li>
 *   <li>{@link GlintCompat#shouldYield()} 为真 —— 光影（Iris）启用了光影包，
 *       或材质包替换过原版附魔光效贴图。此时本模组让位，避免抢优先级。</li>
 * </ol>
 *
 * <p>渲染层表改为<b>惰性构建</b>：默认配置下（不接管）整套 100 多个 {@code RenderType} 根本不会创建。
 */
public final class GlintRenderType {

    /** 三套管线：普通光效 / 透明光效 / 盔甲光效（盔甲那套当前没有注册使用，保留以便将来启用）。 */
    private enum Kind { GLINT, TRANSLUCENT, ARMOR }

    private GlintRenderType() {}

    public static RenderType glint() {
        return select(Kind.GLINT, RenderTypes.glint());
    }

    public static RenderType translucent() {
        return select(Kind.TRANSLUCENT, RenderTypes.glintTranslucent());
    }

    public static RenderType armorGlint() {
        return select(Kind.ARMOR, RenderTypes.armorEntityGlint());
    }

    private static RenderType select(Kind kind, RenderType vanilla) {
        // ① 关闭光效（默认）或外部接管 → 让位，返回原版
        if (GlintCompat.shouldYield()) {
            return vanilla;
        }
        GlintPreset preset = currentPreset();
        if (preset == null || preset.isNone()) {
            return vanilla;
        }
        Map<GlintShape, Map<GlintPreset, RenderType>> byShape = Tables.ALL.get(kind);
        if (byShape == null) {
            return vanilla;
        }
        Map<GlintPreset, RenderType> layers = byShape.get(currentShape());
        return layers == null ? vanilla : layers.getOrDefault(preset, vanilla);
    }

    /** 读取当前颜色；配置尚未就绪时按「不接管」处理。 */
    private static GlintPreset currentPreset() {
        try {
            Object value = AutoTradeConfigs.Trade.ENCHANTMENT_COLOR.getOptionListValue();
            if (value instanceof GlintPreset preset) {
                return preset;
            }
        } catch (Throwable ignored) {
            // 配置未初始化：使用默认（不接管）
        }
        return GlintPreset.NONE;
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

    /** 首次访问才构建渲染层表（类加载惰性）。 */
    private static final class Tables {
        static final Map<Kind, Map<GlintShape, Map<GlintPreset, RenderType>>> ALL = buildAll();

        // 注意：不要把这个方法命名为 build()（无参），否则会与外部 GlintRenderType.build(String,...)
        // 在 create() 的作用域内产生重载解析冲突。
        private static Map<Kind, Map<GlintShape, Map<GlintPreset, RenderType>>> buildAll() {
            Map<Kind, Map<GlintShape, Map<GlintPreset, RenderType>>> all = new EnumMap<>(Kind.class);
            for (Kind kind : Kind.values()) {
                Map<GlintShape, Map<GlintPreset, RenderType>> byShape = new EnumMap<>(GlintShape.class);
                for (GlintShape shape : GlintShape.values()) {
                    Map<GlintPreset, RenderType> byPreset = new EnumMap<>(GlintPreset.class);
                    for (GlintPreset preset : GlintPreset.values()) {
                        // NONE = 不接管，永远不参与渲染层表
                        if (preset.isNone()) {
                            continue;
                        }
                        byPreset.put(preset, create(kind, shape, preset));
                    }
                    byShape.put(shape, byPreset);
                }
                all.put(kind, byShape);
            }
            return all;
        }

        private static RenderType create(Kind kind, GlintShape shape, GlintPreset preset) {
            String name = shape.getStringValue() + "_" + preset.getStringValue();
            return switch (kind) {
                case GLINT -> build("glint_" + name, shape, preset, TextureTransform.GLINT_TEXTURING, null);
                case TRANSLUCENT -> build("glint_translucent_" + name, shape, preset,
                        TextureTransform.GLINT_TEXTURING, OutputTarget.ITEM_ENTITY_TARGET);
                case ARMOR -> build("armor_glint_" + name, shape, preset,
                        TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING, null);
            };
        }
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
