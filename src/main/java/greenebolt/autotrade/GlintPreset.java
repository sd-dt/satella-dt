package greenebolt.autotrade;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

/**
 * 附魔光效颜色预设。
 *
 * <p>{@link #NONE} 排在第一位并作为默认值：含义是「不接管」——本模组完全不干预原版附魔光效
 * （原版 / 材质包 / 光影怎么显示就怎么显示）。其余取值为本模组的彩色光效，
 * 选中后仍然会在检测到光影启用或材质包替换过原版光效贴图时自动让位
 * （判定见 {@link GlintCompat}）。
 */
public enum GlintPreset implements IConfigOptionListEntry {
    NONE("none", "原版光效（不接管）"),
    WHITE("white", "白色"), ORANGE("orange", "橙色"), MAGENTA("magenta", "品红色"),
    LIGHT_BLUE("light_blue", "淡蓝色"), YELLOW("yellow", "黄色"), LIME("lime", "黄绿色"),
    PINK("pink", "粉色"), GRAY("gray", "灰色"), LIGHT_GRAY("light_gray", "淡灰色"),
    CYAN("cyan", "青色"), PURPLE("purple", "紫色"), BLUE("blue", "蓝色"),
    BROWN("brown", "棕色"), GREEN("green", "绿色"), RED("red", "红色"), BLACK("black", "黑色"),
    RAINBOW("rainbow", "彩虹"), LIGHT("light", "柔和原版");

    private final String value;
    private final String displayName;

    GlintPreset(String value, String displayName) { this.value = value; this.displayName = displayName; }
    @Override public String getStringValue() { return value; }
    @Override public String getDisplayName() { return displayName; }
    @Override public IConfigOptionListEntry cycle(boolean forward) {
        return values()[Math.floorMod(ordinal() + (forward ? 1 : -1), values().length)];
    }
    @Override public IConfigOptionListEntry fromString(String value) {
        for (GlintPreset preset : values()) if (preset.value.equalsIgnoreCase(value)) return preset;
        return NONE;
    }
    public String textureName() { return "glint_" + value; }

    /** 是否「不接管」（不修改原版附魔光效）。 */
    public boolean isNone() { return this == NONE; }
}
