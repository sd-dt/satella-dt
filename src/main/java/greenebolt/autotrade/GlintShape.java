package greenebolt.autotrade;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * 附魔光效形态（26.2 官方名版本，移植自 1.21.11 主版）。
 *
 * <p>背景：材质包把脸部拆成大量子模型、层间只有 0.025 像素的 Z 偏移，而默认光效纹理 alpha 全为 255、
 * 客户端 {@code glint.fsh} 的 {@code if (color.a < 0.1) discard;} 永不触发，光效会铺满整个几何体，
 * 于是这些碎层暴露为闪烁。
 *
 * <ul>
 *   <li>{@link #COOL}：纹理带图案化 alpha（暗处 alpha=0），着色器丢弃暗像素，光效只保留在
 *       亮网格线上——闪烁消失，视觉上呈现图案化形态。</li>
 *   <li>{@link #DEFAULT}：纹理不带图案化 alpha，保持原来的整体光膜效果。</li>
 * </ul>
 */
public enum GlintShape implements IConfigOptionListEntry {
    COOL("cool", "炫酷"),
    DEFAULT("default", "默认");

    private final String value;
    private final String displayName;

    GlintShape(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    @Override
    public String getStringValue() {
        return this.value;
    }

    @Override
    public String getDisplayName() {
        // 注意：MaLiLib 的 translate(String, Object...) 第二个参数是格式参数，
        // 带回退值的正确 API 是 getTranslatedOrFallback(key, fallback)。
        return StringUtils.getTranslatedOrFallback("satella.glint_shape." + this.value, this.displayName);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        int next = Math.floorMod(this.ordinal() + (forward ? 1 : -1), values().length);
        return values()[next];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        for (GlintShape shape : values()) {
            if (shape.value.equalsIgnoreCase(value)) {
                return shape;
            }
        }
        return COOL;
    }

    /** 纹理所在子目录名：{@code textures/misc/<folder>/glint_<color>.png}。 */
    public String textureFolder() {
        return this.value;
    }
}
