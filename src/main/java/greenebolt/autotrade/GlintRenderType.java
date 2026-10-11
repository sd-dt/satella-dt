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
 * 附魔光效渲染层（26.2 官方名版本，2026-10-11 起以官方新版 Satella 的实现为蓝本同步）。
 *
 * <p>每种「颜色」（{@link GlintPreset}）对应一套独立 {@link RenderType}，纹理为
 * {@code satella:textures/misc/glint_<颜色>.png}（与蓝本一致的扁平目录；蓝本的这套贴图
 * 与旧版「默认形态」贴图逐字节相同，旧「光效形态」二选一随本次同步一并移除）。
 *
 * <p>三套管线与蓝本一一对应：普通光效 / 透明光效（物品实体目标）/ 盔甲光效
 * （{@code ARMOR_ENTITY_GLINT_TEXTURING} 变换 + {@code VIEW_OFFSET_Z_LAYERING} 分层）。
 * 盔甲那套自本版起重新注册（{@code EquipmentLayerRendererMixin}，与蓝本一致）：
 * 「附魔显示颜色」选中颜色时物品与盔甲一起变色；默认（NONE）时两处都不接管。
 *
 * <p><b>不接管的情形</b>（直接返回原版 {@code RenderType}，不创建任何自定义渲染层）：
 * <ol>
 *   <li>「附魔显示颜色」= {@link GlintPreset#NONE}（默认值，保持原版光效）；</li>
 *   <li>{@link GlintCompat#shouldYield()} 为真 —— 光影（Iris）启用了光影包，
 *       或材质包替换过原版附魔光效贴图。此时本模组让位，避免抢优先级。</li>
 * </ol>
 *
 * <p>渲染层表保持<b>惰性构建</b>：默认配置下（不接管）整套渲染层根本不会创建。
 */
public final class GlintRenderType {

    /** 三套管线：普通光效 / 透明光效 / 盔甲光效。 */
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
        // ① 关闭（默认，NONE = 原版光效不接管）或外部接管（光影/材质包）→ 让位，返回原版
        if (GlintCompat.shouldYield()) {
            return vanilla;
        }
        GlintPreset preset = currentPreset();
        if (preset == null || preset.isNone()) {
            return vanilla;
        }
        Map<Kind, Map<GlintPreset, RenderType>> tables = Tables.ALL;
        if (tables == null) {
            return vanilla;
        }
        Map<GlintPreset, RenderType> layers = tables.get(kind);
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

    /** 首次访问才构建渲染层表（类加载惰性）。 */
    private static final class Tables {
        static final Map<Kind, Map<GlintPreset, RenderType>> ALL = buildAll();

        private static Map<Kind, Map<GlintPreset, RenderType>> buildAll() {
            Map<Kind, Map<GlintPreset, RenderType>> all = new EnumMap<>(Kind.class);
            for (Kind kind : Kind.values()) {
                Map<GlintPreset, RenderType> byPreset = new EnumMap<>(GlintPreset.class);
                for (GlintPreset preset : GlintPreset.values()) {
                    // NONE = 不接管，永远不参与渲染层表（原版光效直接返回）
                    if (preset.isNone()) {
                        continue;
                    }
                    byPreset.put(preset, create(kind, preset));
                }
                all.put(kind, byPreset);
            }
            return all;
        }

        private static RenderType create(Kind kind, GlintPreset preset) {
            return switch (kind) {
                case GLINT -> build("glint_" + preset.getStringValue(), preset,
                        TextureTransform.GLINT_TEXTURING, null);
                case TRANSLUCENT -> build("glint_translucent_" + preset.getStringValue(), preset,
                        TextureTransform.GLINT_TEXTURING, OutputTarget.ITEM_ENTITY_TARGET);
                case ARMOR -> build("armor_glint_" + preset.getStringValue(), preset,
                        TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING, null);
            };
        }
    }

    /** 与蓝本一致：GLINT 管线 + 自定义贴图；盔甲用 ARMOR 变换并叠加分层。 */
    private static RenderType build(String name, GlintPreset preset,
                                    TextureTransform transform, OutputTarget target) {
        Identifier texture = Identifier.fromNamespaceAndPath("satella",
                "textures/misc/" + preset.textureName() + ".png");
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
