package greenebolt.autotrade;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * 主手手持激流三叉戟、副手有食物时的右键行为。
 *
 * <ul>
 *   <li>{@link #FLIGHT_FIRST}：照常放激流，不吃副手食物。<b>默认值</b>。</li>
 *   <li>{@link #EAT_FIRST}：压制激流（不发主手三叉戟的使用包），把进食完全交给原版流程。
 *       进食必须由原版完成：食物的实际消耗发生在服务端 {@code tickItemStackUsage}，自己调用
 *       {@code useItem} 只会得到「有粒子但吃不下」。详见 {@code ToolInteractionHandler#isEatFirstActive}。</li>
 * </ul>
 *
 * <p>移植自「更好的激流三叉戟」（RiptideConfig.OffhandFoodPriority，26.2 版）。
 */
public enum OffhandFoodPriority implements IConfigOptionListEntry {
    FLIGHT_FIRST("flight_first", "优先激流"),
    EAT_FIRST("eat_first", "优先进食");

    private final String value;
    private final String displayName;

    OffhandFoodPriority(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    @Override
    public String getStringValue() {
        return this.value;
    }

    @Override
    public String getDisplayName() {
        return StringUtils.getTranslatedOrFallback("satella.offhand_food." + this.value, this.displayName);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        int next = Math.floorMod(this.ordinal() + (forward ? 1 : -1), values().length);
        return values()[next];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        if (value != null) {
            for (OffhandFoodPriority priority : values()) {
                if (priority.value.equalsIgnoreCase(value)) {
                    return priority;
                }
            }
        }
        return FLIGHT_FIRST;
    }

    public boolean isEatFirst() {
        return this == EAT_FIRST;
    }
}
