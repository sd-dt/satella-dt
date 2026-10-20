package greenebolt.autotrade;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔光效渲染层（26.3 官方名版本）。
 *
 * <p>26.3 起原版把「光效层」改成<b>双采样器</b>结构：{@code Sampler0} 是物品/盔甲
 * 所在图集（{@code minecraft:blocks} / {@code minecraft:items} / 盔甲贴图），
 * {@code GlintSampler} 才是附魔光效贴图，光效 RenderType 在模型烘焙时按图集
 * 一次性生成（{@code Sheets} / {@code EquipmentLayerRenderer}），不再有 26.2 的
 * 无参 {@code RenderTypes.glint()}。因此本版改为：<b>保持原版 Sampler0 不变</b>，
 * 只把 {@code GlintSampler} 换成 {@code satella:textures/misc/glint_<颜色>.png}，
 * 管线、变换、分层全部照抄原版对应类型。
 *
 * <p>三套管线：普通物品（{@code ITEM_CUTOUT_GLINT}）、透明物品
 * （{@code ITEM_TRANSLUCENT_GLINT} + OIT）、盔甲
 * （{@code ARMOR_CUTOUT_NO_CULL_GLINT} + {@code ARMOR_ENTITY_GLINT_TEXTURING}
 * + {@code VIEW_OFFSET_Z_LAYERING}）。
 *
 * <p><b>不接管的情形</b>（直接返回原版 {@code RenderType}，不创建任何自定义渲染层）：
 * <ol>
 *   <li>「附魔显示颜色」= {@link GlintPreset#NONE}（默认值，保持原版光效）；</li>
 *   <li>{@link GlintCompat#shouldYield()} 为真 —— 光影（Iris）启用了光影包，
 *       或材质包替换过原版附魔光效贴图。此时本模组让位，避免抢优先级。</li>
 * </ol>
 *
 * <p>渲染层按「颜色 + 种类 + 图集」惰性构建并缓存：默认配置下（不接管）根本不创建。
 */
public final class GlintRenderType {

    /** 三套管线：普通物品光效 / 透明物品光效 / 盔甲光效。 */
    private enum Kind { ITEM_CUTOUT, ITEM_TRANSLUCENT, ARMOR }

    private static final Map<String, RenderType> CACHE = new ConcurrentHashMap<>();

    private GlintRenderType() {}

    /** 物品光效：{@code atlasTexture} 为原版传入的图集位置（items 或 blocks）。 */
    public static RenderType forItem(Identifier atlasTexture, boolean translucent) {
        RenderType vanilla = translucent
                ? RenderTypes.itemTranslucentGlint(atlasTexture)
                : RenderTypes.itemCutoutGlint(atlasTexture);
        return select(translucent ? Kind.ITEM_TRANSLUCENT : Kind.ITEM_CUTOUT, atlasTexture, vanilla);
    }

    /** 盔甲光效：{@code armorTexture} 为原版传入的盔甲层贴图。 */
    public static RenderType forArmor(Identifier armorTexture) {
        return select(Kind.ARMOR, armorTexture, RenderTypes.armorCutoutNoCullGlint(armorTexture));
    }

    private static RenderType select(Kind kind, Identifier texture, RenderType vanilla) {
        // ① 关闭（默认，NONE = 原版光效不接管）或外部接管（光影/材质包）→ 让位，返回原版
        if (GlintCompat.shouldYield()) {
            return vanilla;
        }
        GlintPreset preset = currentPreset();
        if (preset == null || preset.isNone()) {
            return vanilla;
        }
        String key = preset.getStringValue() + '|' + kind.name() + '|' + texture;
        return CACHE.computeIfAbsent(key, ignored -> build(kind, preset, texture));
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

    /** 与原版对应类型逐项对齐，只替换 GlintSampler。 */
    private static RenderType build(Kind kind, GlintPreset preset, Identifier texture) {
        Identifier glintTexture = Identifier.fromNamespaceAndPath("satella",
                "textures/misc/" + preset.textureName() + ".png");
        RenderSetup.RenderSetupBuilder builder = switch (kind) {
            case ITEM_CUTOUT -> RenderSetup.builder(RenderPipelines.ITEM_CUTOUT_GLINT)
                    .withTexture("Sampler0", texture)
                    .withTexture("GlintSampler", glintTexture)
                    .setTextureTransform(TextureTransform.GLINT_TEXTURING)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
            case ITEM_TRANSLUCENT -> RenderSetup.builder(RenderPipelines.ITEM_TRANSLUCENT_GLINT)
                    .setOitPipelines(RenderPipelines.OIT_ITEM_GLINT)
                    .withTexture("Sampler0", texture)
                    .withTexture("GlintSampler", glintTexture)
                    .setTextureTransform(TextureTransform.GLINT_TEXTURING)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
            case ARMOR -> RenderSetup.builder(RenderPipelines.ARMOR_CUTOUT_NO_CULL_GLINT)
                    .withTexture("Sampler0", texture)
                    .withTexture("GlintSampler", glintTexture)
                    .setTextureTransform(TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING)
                    .useLightmap()
                    .useOverlay()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
        };
        return greenebolt.autotrade.mixin.RenderTypeInvoker.autoTrade$create(
                "satella_" + kind.name().toLowerCase() + "_glint", builder.createRenderSetup());
    }
}
