package greenebolt.autotrade;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * 三叉戟交互优先级：右键方块时「方块」与「工具」谁优先，三种模式。
 *
 * <p>原版 {@code Minecraft#startUseItem} 的顺序是「主手方块 → 主手物品 → 副手方块 → 副手物品」：
 * 命中方块就先走方块交互，物品（激流三叉戟 / 弓箭）便用不出来。本选项决定怎么改写这个顺序。
 *
 * <ul>
 *   <li>{@link #TOOL_FIRST}：<b>工具优先</b>——任何方块前都使用工具，不交互方块
 *       （激流三叉戟、已蓄力弩、有箭的弓）；工具条件不满足时（弩未蓄力、弓无箭）仍走原版流程。</li>
 *   <li>{@link #INTERACTABLE_FIRST}：<b>可交互方块优先</b>——先让原版处理方块：可交互方块正常交互；
 *       不可交互方块由原版返回 {@code PASS} 后自然回退到物品使用（即触发激流）。
 *       <b>默认值</b>，最接近原版行为。</li>
 *   <li>{@link #PLACE_FIRST}：<b>放置方块优先</b>——不接管方块交互、也不触发激流；
 *       原版流程会继续走到副手，副手的方块照常放置。</li>
 * </ul>
 *
 * <p>拦截不依赖方块交互距离：注入点只在准星确实命中方块时被调用，而准星由
 * {@code BLOCK_INTERACTION_RANGE} 属性决定，因此任意交互距离下都生效。
 *
 * <p>纯客户端实现：只在客户端取消方块交互并改发「使用物品」包，服务器无需安装本模组。
 */
public enum ToolInteractionPriority implements IConfigOptionListEntry {
    TOOL_FIRST("tool_first", "工具优先"),
    INTERACTABLE_FIRST("interactable_first", "可交互方块优先"),
    PLACE_FIRST("place_first", "放置方块优先");

    private final String value;
    private final String displayName;

    ToolInteractionPriority(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    @Override
    public String getStringValue() {
        return this.value;
    }

    @Override
    public String getDisplayName() {
        return StringUtils.getTranslatedOrFallback("satella.trident_mode." + this.value, this.displayName);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        int next = Math.floorMod(this.ordinal() + (forward ? 1 : -1), values().length);
        return values()[next];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        if (value != null) {
            // 旧两模式配置的兼容：原来的「交互优先」= 现在的「可交互方块优先」
            if (value.equalsIgnoreCase("interaction_first")) {
                return INTERACTABLE_FIRST;
            }
            for (ToolInteractionPriority mode : values()) {
                if (mode.value.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
        }
        return INTERACTABLE_FIRST;
    }

    public boolean isToolFirst() {
        return this == TOOL_FIRST;
    }

    public boolean isPlaceFirst() {
        return this == PLACE_FIRST;
    }
}
