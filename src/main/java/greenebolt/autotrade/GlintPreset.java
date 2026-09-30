package greenebolt.autotrade;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

public enum GlintPreset implements IConfigOptionListEntry {
    WHITE("white", "白色"), ORANGE("orange", "橙色"), MAGENTA("magenta", "品红色"),
    LIGHT_BLUE("light_blue", "淡蓝色"), YELLOW("yellow", "黄色"), LIME("lime", "黄绿色"),
    PINK("pink", "粉色"), GRAY("gray", "灰色"), LIGHT_GRAY("light_gray", "淡灰色"),
    CYAN("cyan", "青色"), PURPLE("purple", "紫色"), BLUE("blue", "蓝色"),
    BROWN("brown", "棕色"), GREEN("green", "绿色"), RED("red", "红色"), BLACK("black", "黑色"),
    RAINBOW("rainbow", "彩虹"), LIGHT("light", "柔和原版"), NONE("none", "关闭光效");

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
        return WHITE;
    }
    public String textureName() { return "glint_" + value; }
}
