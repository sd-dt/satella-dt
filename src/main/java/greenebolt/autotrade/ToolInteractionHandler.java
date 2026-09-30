package greenebolt.autotrade;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * 右键行为的判定中心（26.2 官方名版本）。
 *
 * <p>被 {@code mixin/ToolInteractionMixin} 在 {@code MultiPlayerGameMode#useItemOn} 与
 * {@code #useItem} 两处调用，负责回答三个问题：
 * <ol>
 *   <li><b>工具优先时是否接管</b>（{@link #decide}）——激流三叉戟、已蓄力弩、有箭的弓；</li>
 *   <li><b>优先进食是否生效</b>（{@link #isEatFirstActive}）——主手激流三叉戟 + 副手有食物
 *       （或本次按住已经吃过）；</li>
 *   <li><b>主手这次物品使用是否跳过</b>（{@link #shouldSkipMainHandUse}）——优先进食或不触发激流时。</li>
 * </ol>
 *
 * <p>拦截不依赖交互距离：注入点在 {@code useItemOn} 上，而该方法只在准星确实命中方块时被调用
 * （准星由 {@code Player#blockInteractionRange()} 决定）。
 */
public final class ToolInteractionHandler {

    private ToolInteractionHandler() {}

    /**
     * 一次判定结果。
     *
     * @param handled    是否由工具接管（true 时调用方需取消方块交互）
     * @param selfInvoke 是否由注入点自己调用 {@code useItem}。
     *                   true 用于激流三叉戟（原版回退路径会因 {@code use()} 返回 FAIL 而中断）；
     *                   false 用于弩/弓（必须让原版单次回退，否则使用状态会被重复调用冲掉）。
     */
    public static final class Decision {
        private final boolean handled;
        private final boolean selfInvoke;

        private Decision(boolean handled, boolean selfInvoke) {
            this.handled = handled;
            this.selfInvoke = selfInvoke;
        }

        public boolean handled() {
            return this.handled;
        }

        public boolean selfInvoke() {
            return this.selfInvoke;
        }
    }

    private static final Decision PASS = new Decision(false, false);
    private static final Decision HAND_OFF = new Decision(true, false);
    private static final Decision SELF_INVOKE = new Decision(true, true);

    // ------------------------------------------------------------------ 模式读取

    /** 当前的三叉戟交互模式；配置未就绪时按「可交互方块优先」处理。 */
    public static ToolInteractionPriority mode() {
        try {
            Object value = AutoTradeConfigs.Trade.TOOL_INTERACTION_PRIORITY.getOptionListValue();
            if (value instanceof ToolInteractionPriority m) {
                return m;
            }
        } catch (Throwable ignored) {
            // 配置未初始化
        }
        return ToolInteractionPriority.INTERACTABLE_FIRST;
    }

    /** 当前是否「工具优先」（任何方块前都用工具）。 */
    public static boolean isToolFirst() {
        return mode().isToolFirst();
    }

    /** 当前是否「放置方块优先」（不触发激流）。 */
    public static boolean isPlaceFirst() {
        return mode().isPlaceFirst();
    }

    /** 当前是否启用「优先进食」（只表示配置项，实际是否生效还要看手持物）。 */
    public static boolean isEatFirst() {
        try {
            Object value = AutoTradeConfigs.Trade.OFFHAND_FOOD_PRIORITY.getOptionListValue();
            return value instanceof OffhandFoodPriority p && p.isEatFirst();
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 工具判定

    /**
     * 判定本次右键是否应由工具接管（只在「工具优先」模式下调用）。
     *
     * <p><b>返回值的选择依据</b>（详见 {@link Decision#selfInvoke()}）：
     * 弩与弓的 {@code use()} 在正常条件下返回 CONSUME，走原版 PASS 回退即可；
     * 而激流三叉戟在陆地会返回 FAIL，走回退会让原版 {@code startUseItem} 直接结束，
     * 因此必须由注入点自己发包并返回 SUCCESS。
     */
    public static Decision decide(Player player, InteractionHand hand) {
        if (player == null || !isToolFirst()) {
            return PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return PASS;
        }
        ToolKind kind = classify(stack);
        if (kind == ToolKind.NONE || !isAvailable(player, stack, kind)) {
            return PASS;
        }
        return kind == ToolKind.TRIDENT ? SELF_INVOKE : HAND_OFF;
    }

    private enum ToolKind { TRIDENT, CROSSBOW, BOW, NONE }

    private static ToolKind classify(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof TridentItem) {
            return ToolKind.TRIDENT;
        }
        if (item instanceof CrossbowItem) {
            return ToolKind.CROSSBOW;
        }
        if (item instanceof BowItem) {
            return ToolKind.BOW;
        }
        return ToolKind.NONE;
    }

    private static boolean isAvailable(Player player, ItemStack stack, ToolKind kind) {
        return switch (kind) {
            case TRIDENT -> riptideStrength(player, stack) > 0.0F;
            case CROSSBOW -> CrossbowItem.isCharged(stack);
            case BOW -> hasArrow(player);
            case NONE -> false;
        };
    }

    // ------------------------------------------------------------------ 进食与跳过

    /**
     * 「优先进食」当前是否生效。
     *
     * <p>判据是<b>副手有食物</b>，而不是「吃得下」——吃完后玩家通常已经吃饱，若按饥饿度判断，
     * 右键会在吃完的瞬间落回激流（表现为「吃完就往前飞出」）。另外补上「本次按住已经吃过」，
     * 覆盖刚好吃完最后一个、副手已空的情况。
     */
    public static boolean isEatFirstActive(Player player) {
        if (player == null || !isEatFirst()) {
            return false;
        }
        if (!isRiptideTrident(player, player.getMainHandItem())) {
            return false;
        }
        return isFood(player.getOffhandItem()) || OffhandFoodState.ateThisPress();
    }

    /**
     * 主手这次 {@code useItem} 是否应当跳过（注入点返回 {@code PASS}）。
     *
     * <p>两种情形：
     * <ul>
     *   <li><b>优先进食</b>：原版 {@code startUseItem} 即使方块交互被让开，仍会先对主手三叉戟调用一次
     *       {@code useItem} 并把使用包发给服务端。服务端一旦因此进入「使用中」，紧随其后的副手食物
     *       会在 {@code LivingEntity#startUsingItem} 的 {@code !isUsingItem()} 守卫上被丢弃 ——
     *       表现为「有进食动画但吃不下、停下后放激流」。所以这里直接取消主手使用，让原版走到副手。</li>
     *   <li><b>放置方块优先</b>：不触发激流；跳过主手使用后，{@code startUseItem} 会继续到副手，
     *       由副手把方块放下去。</li>
     * </ul>
     */
    public static boolean shouldSkipMainHandUse(Player player) {
        if (player == null || !isRiptideTrident(player, player.getMainHandItem())) {
            return false;
        }
        return isEatFirstActive(player) || isPlaceFirst();
    }

    // ------------------------------------------------------------------ 物品判定

    /** 是否为「附魔激流三叉戟」（与原版 {@code TridentItem.use} 同源的判定）。 */
    public static boolean isRiptideTrident(Player player, ItemStack stack) {
        return player != null && stack != null && !stack.isEmpty()
                && stack.getItem() instanceof TridentItem
                && riptideStrength(player, stack) > 0.0F;
    }

    /** 是否为可食用物品（1.21 起食物是数据组件）。 */
    public static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return stack.has(DataComponents.FOOD);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 与原版 {@code TridentItem.use} 同源的激流强度读取（> 0 表示有激流附魔）。 */
    public static float riptideStrength(Player player, ItemStack stack) {
        try {
            return EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    /** 背包或副手是否有箭（与原版弓的取弹判定一致）。 */
    private static boolean hasArrow(Player player) {
        if (isArrow(player.getOffhandItem())) {
            return true;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isArrow(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isArrow(ItemStack stack) {
        return stack.getItem() == Items.ARROW || stack.getItem() == Items.TIPPED_ARROW;
    }
}
