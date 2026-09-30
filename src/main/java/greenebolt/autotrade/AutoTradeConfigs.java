package greenebolt.autotrade;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AutoTradeConfigs implements IConfigHandler {
    public static final String MOD_ID = "satella";
    private static final AutoTradeConfigs INSTANCE = new AutoTradeConfigs();
    private static Path path;

    /** 让热键在游戏内和打开的界面中都能触发 */
    private static final KeybindSettings KEYBIND_ANY_CONTEXT = KeybindSettings.create(
            KeybindSettings.Context.ANY, KeyAction.PRESS, false, true, false, true);

    public static final class Trade {
        public static final ConfigBoolean ENABLED = new ConfigBoolean("启用自动交易", false, "启用自动交易");
        public static final ConfigOptionList MODE = new ConfigOptionList("交易模式", TradeMode.AUTO, "单次或自动轮询交易");
        public static final ConfigInteger TICK_INTERVAL = new ConfigInteger("交易间隔", 2, 1, 20, "轮询间隔（游戏刻）");
        public static final ConfigInteger TRADES_PER_SESSION = new ConfigInteger("每次交易次数", 20, 1, 100000, "每次打开交易的最大成交数量");
        public static final ConfigString INPUT_ITEM_1 = new ConfigString("输入物品1", "绿宝石", "物品 ID 或当前语言显示名");
        public static final ConfigString INPUT_ITEM_2 = new ConfigString("输入物品2", "", "可选的第二种输入物品");
        public static final ConfigString OUTPUT_ITEM = new ConfigString("输出物品", "铁锭", "物品 ID 或当前语言显示名");
        public static final ConfigBoolean DROP_OUTPUTS = new ConfigBoolean("交易后丢弃输出物品", false, "将交易结果丢弃到地面");
        public static final ConfigBoolean TRADE_GUI = new ConfigBoolean("交易GUI", false,
                "开启时正常显示村民交易界面且交易期间保持打开（手动关闭后会在下个交易间隔自动重新打开）；关闭时隐藏界面后台交易");
        public static final ConfigInteger REFRESH_TRADE_GUI = new ConfigInteger("刷新交易界面", 0, 0, 1200,
                "每隔多少 gt 关闭一次村民交易界面（关闭后下个交易间隔会自动重新打开并刷新交易）；0 = 不主动刷新\n单位：gt");
        public static final ConfigHotkey TOGGLE_KEY = new ConfigHotkey("自动交易开关键", "", KEYBIND_ANY_CONTEXT,
                "按下开启或关闭自动交易（默认未绑定，游戏内和界面中均可触发）");
        public static final ConfigHotkey MODE_KEY = new ConfigHotkey("切换交易模式键", "", KEYBIND_ANY_CONTEXT,
                "在 单次交易 与 自动交易 之间切换（默认未绑定，游戏内和界面中均可触发）");
        public static final ConfigInteger CRAFT_RESIDUE = new ConfigInteger("残余", 4, 0, 32,
                "残差合成补料与切石补料时，若背包中某材料堆数量 P 满足 0.5*P < 该值，则跳过该堆（单位：个）");
        public static final ConfigInteger AUTOMATION_INTERVAL = new ConfigInteger("自动化周期", 4, 1, 64,
                "自动化每隔多少游戏刻执行一次合成/切石动作");
        public static final ConfigOptionList CRAFT_FILL_MODE = new ConfigOptionList("合成填入材料模式",
                CraftFillMode.SIMULATED_CLICK, "选择自动合成填入材料的方式：模拟点击或配方书");
        public static final ConfigInteger RECIPE_FILL_ITERATIONS = new ConfigInteger("配方填入次数", 32, 1, 64,
                "配方书模式下，每次自动化周期最多请求原版配方书填入材料多少次");
        public static final ConfigBoolean AUTOMATION = new ConfigBoolean("自动化开关", false,
                "总开关。开启后自动寻找附近的工作台/切石机，隐藏界面并按自动化模式持续执行");
        public static final ConfigOptionList AUTOMATION_MODE = new ConfigOptionList("自动化模式", AutomationMode.CRAFTING,
                "合成：按当前 Item Scroller 配方自动合成\n切石：把切石输入物品切石成输出物品后丢弃");
        public static final ConfigHotkey AUTOMATION_KEY = new ConfigHotkey("自动化开关键", "", KEYBIND_ANY_CONTEXT,
                "按下开启或关闭自动化（默认未绑定，游戏内和界面中均可触发）");
        public static final ConfigHotkey AUTOMATION_MODE_KEY = new ConfigHotkey("自动化模式键", "", KEYBIND_ANY_CONTEXT,
                "在 合成 与 切石 之间切换自动化模式（默认未绑定，游戏内和界面中均可触发），切换后在物品栏上方提示当前模式");
        public static final ConfigBoolean GUI_DISPLAY = new ConfigBoolean("GUI显示", false,
                "开启时正常显示工作台/切石机的界面（可随时关闭界面，自动化会重新打开）；关闭时隐藏界面后台执行");
        public static final ConfigString STONECUTTING_INPUT = new ConfigString("切石输入物品", "石头",
                "放入切石机的物品，支持物品显示名、物品 id（如 minecraft:stone）或省略 minecraft: 的 id（如 stone）");
        public static final ConfigString STONECUTTING_OUTPUT = new ConfigString("切石输出物品", "石砖",
                "切石要选中的输出成品，支持物品显示名、物品 id 或省略 minecraft: 的 id\n没有匹配的切石配方时会在物品栏上方提示“未找到目标配方”");
        public static final ConfigBoolean BETTER_CROSSBOW = new ConfigBoolean(
                "更NB的弩", false,
                "手持弩长按右键时，持续使用并按周期重复执行右键");

        public static final ConfigInteger BETTER_CROSSBOW_INTERVAL = new ConfigInteger(
                "更NB的弩周期", 20, 1, 100000,
                "更NB的弩每隔多少游戏刻执行一次周期性右键");

        public static final ConfigBoolean SERVER_SHULKER_COMPAT = new ConfigBoolean(
                "服务器快捷潜影盒兼容", false,
                "让 Item Scroller、Inventory Profiles Next 和 Tweakeroo 忽略指定潜影盒组件，配置文件位于 config/satella/ignored-components.txt");

        public static final ConfigOptionList ENCHANTMENT_COLOR = new ConfigOptionList("附魔显示颜色", GlintPreset.WHITE,
                "点击切换预设附魔光效颜色");

        public static final ConfigOptionList ENCHANTMENT_SHAPE = new ConfigOptionList(
                "光效形态", GlintShape.COOL,
                "点击切换附魔光效形态\n炫酷：光效只保留在亮网格线上，呈图案化效果，可消除自定义模型（如脸部材质包）在眼睛部位的闪烁\n默认：完整的整体光膜效果");

        public static final ConfigOptionList TOOL_INTERACTION_PRIORITY = new ConfigOptionList(
                "三叉戟交互", ToolInteractionPriority.INTERACTABLE_FIRST,
                "点击切换右键方块时的处理方式\n"
                        + "工具优先：任何方块前都使用工具（激流三叉戟 / 已蓄力弩 / 有箭的弓），不交互方块\n"
                        + "可交互方块优先：右键可交互方块时正常交互；右键不可交互方块时使用工具（即触发激流，默认）\n"
                        + "放置方块优先：不触发激流，方块交互交给原版流程自己走（副手的方块照常放置）\n"
                        + "不论方块交互距离设置为多少都生效\n纯客户端功能，服务器无需安装");

        public static final ConfigOptionList OFFHAND_FOOD_PRIORITY = new ConfigOptionList(
                "副手食物", OffhandFoodPriority.FLIGHT_FIRST,
                "主手手持激流三叉戟、副手有食物时，右键的行为\n"
                        + "优先激流：照常放激流，不吃副手食物（默认）\n"
                        + "优先进食：压制激流，把进食交给原版流程；吃完到松开右键前都不再触发激流\n"
                        + "「可交互方块优先 / 放置方块优先」下，瞄着可交互方块时仍会正常与方块交互\n"
                        + "纯客户端功能，服务器无需安装");

        public static final ConfigStringList DROP_BLOCK_ITEMS = new ConfigStringList("拦截目标物品丢弃", ImmutableList.of(),
                "列表内的物品仅可通过“拿起物品后光标移出界面点击丢弃”这一种方式丢弃：手持 Q / Ctrl+Q、背包/容器内按 Q / Ctrl+Q 等全部拦截\n"
                        + "支持物品显示名、物品 id（如 minecraft:diamond）或省略 minecraft: 的 id（如 diamond）");

        public static final ConfigStringList ST_RULES = new ConfigStringList(
                "多环定位规则", ImmutableList.of(),
                "用 /st rules 指令打开编辑器配置。每条格式：最小距离-最大距离:种子[:数据包1|数据包2]\n"
                        + "距离 = 检索中心（玩家位置）到世界原点 (0,0) 的切比雪夫距离（方块）\n"
                        + "数据包为 config/satella/datapacks 下的 zip 文件名（可省略 .zip），用 | 分隔；省略数据包部分时使用全部\n"
                        + "例：0-4096:123456 与 4097-999999:654321:tectonic-datapack-3.0.18|Dungeons and Taverns v5.1.0\n"
                        + "未命中任何环时，使用 /st seed 设置的全局种子且不加载任何数据包 zip");
        /** 配置界面“交易”分类页 */
        public static final ImmutableList<IConfigBase> TRADE_OPTIONS = ImmutableList.of(ENABLED, MODE, TICK_INTERVAL,
                TRADES_PER_SESSION, INPUT_ITEM_1, INPUT_ITEM_2, OUTPUT_ITEM, DROP_OUTPUTS, TRADE_GUI, REFRESH_TRADE_GUI, TOGGLE_KEY, MODE_KEY);

        /** 配置界面“自动化”分类页 */
        public static final ImmutableList<IConfigBase> AUTOMATION_OPTIONS = ImmutableList.of(AUTOMATION,
                AUTOMATION_MODE, AUTOMATION_KEY, AUTOMATION_MODE_KEY, AUTOMATION_INTERVAL,
                CRAFT_FILL_MODE, RECIPE_FILL_ITERATIONS, CRAFT_RESIDUE, STONECUTTING_INPUT, STONECUTTING_OUTPUT, GUI_DISPLAY);

        /** 配置界面“杂项”分类页（「副手食物」紧随「三叉戟交互」之后） */
        public static final ImmutableList<IConfigBase> MISC_OPTIONS = ImmutableList.of(
                BETTER_CROSSBOW, BETTER_CROSSBOW_INTERVAL, SERVER_SHULKER_COMPAT, ENCHANTMENT_COLOR,
                ENCHANTMENT_SHAPE, TOOL_INTERACTION_PRIORITY, OFFHAND_FOOD_PRIORITY, DROP_BLOCK_ITEMS, ST_RULES);

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.<IConfigBase>builder()
                .addAll(TRADE_OPTIONS).addAll(AUTOMATION_OPTIONS).addAll(MISC_OPTIONS).build();
    }

    public static void register() {
        path = FileUtils.getConfigDirectory().resolve(MOD_ID).resolve("Satella.json");
        ConfigManager.getInstance().registerConfigHandler(MOD_ID, INSTANCE);
        INSTANCE.load();
    }

    /** 供规则编辑器等外部界面立即落盘配置 */
    public static void saveNow() {
        INSTANCE.save();
    }

    public static boolean isEnabled() { return Trade.ENABLED.getBooleanValue(); }
    public static boolean isAutoMode() { return Trade.MODE.getOptionListValue() == TradeMode.AUTO; }

    @Override public void load() {
        if (path != null && Files.exists(path)) {
            JsonElement json = JsonUtils.parseJsonFile(path);
            if (json != null && json.isJsonObject()) {
                migrateLegacyOptionNames(json.getAsJsonObject());
                ConfigUtils.readConfigBase(json.getAsJsonObject(), "Trade", Trade.OPTIONS);
            }
        }
    }

    /**
     * 旧版本配置项改名后的兼容：把旧键的值搬到新键上。
     *
     * <p>「工具交互优先级」（工具优先 / 交互优先 两模式）已改名为「三叉戟交互」（三模式），
     * 不迁移的话升级后会静默回到默认值。旧值 {@code interaction_first} 由
     * {@code ToolInteractionPriority.fromString} 接成「可交互方块优先」。
     */
    private static void migrateLegacyOptionNames(JsonObject root) {
        JsonElement tradeElement = root.get("Trade");
        if (tradeElement == null || !tradeElement.isJsonObject()) {
            return;
        }
        JsonObject trade = tradeElement.getAsJsonObject();
        if (trade.has("工具交互优先级") && !trade.has("三叉戟交互")) {
            trade.add("三叉戟交互", trade.get("工具交互优先级"));
        }
    }

    @Override public void save() {
        if (path != null) {
            FileUtils.createDirectoriesIfMissing(path.getParent());
            JsonObject root = new JsonObject();
            ConfigUtils.writeConfigBase(root, "Trade", Trade.OPTIONS);
            JsonUtils.writeJsonToFile(root, path);
        }
    }
}
