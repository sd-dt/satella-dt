package greenebolt.autotrade.mixin;

import greenebolt.autotrade.AutoTrade;
import greenebolt.autotrade.OffhandFoodState;
import greenebolt.autotrade.ToolInteractionHandler;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「工具交互」的三种模式 + 副手食物，共四处注入。
 *
 * <p>原版 {@code Minecraft#startUseItem} 的实际流程（26.2 反编译核对，与 1.21.11 结构相同）：
 * <pre>
 *   for (InteractionHand hand : InteractionHand.values()) {          // 主手 → 副手
 *       stack = player.getItemInHand(hand);
 *       if (!stack.isItemEnabled(...)) return;
 *       if (hitResult != null) switch (hitResult.getType()) {
 *           case BLOCK:
 *               r = gameMode.useItemOn(player, hand, hit);            // ← 注入点 ① / ②
 *               if (r instanceof Success) { 挥手; return; }
 *               if (r instanceof Fail)    return;                     // 注意：FAIL 会中断整轮
 *           case ENTITY: ... ; case MISS: 什么都不做
 *       }
 *       if (stack.isEmpty()) continue;
 *       r2 = gameMode.useItem(player, hand);                          // ← 注入点 ③ / ④
 *       if (r2 instanceof Success) { 挥手; return; }
 *   }
 * </pre>
 *
 * <p>三种模式（配置「工具交互」）分别改写这条链的不同环节：
 * <ul>
 *   <li><b>工具优先</b>：注入点 ①——命中方块时直接接管，返回 {@code SUCCESS} 让原版结束本次右键。
 *       激流三叉戟自己补一次 {@code useItem}（原版回退会因陆地 {@code use()} 返回 FAIL 而中断），
 *       弩/弓返回 {@code PASS} 由原版单次回退调用。</li>
 *   <li><b>可交互方块优先</b>：注入点 ②——可交互方块由原版交互成功并结束（返回 {@code SUCCESS}，
 *       这里不干预）；不可交互方块原版返回 {@code PASS}（极限情况会返回 {@code FAIL}），
 *       这里<b>显式</b>改用工具并返回 {@code SUCCESS}。不再依赖「原版自然回退到 useItem」：
 *       {@code FAIL} 在原版里会直接中断整轮循环，而副手也可能抢先把方块放下去 —— 两者都会
 *       表现为「右击不可交互方块完全没反应 / 反而放了副手方块」。命中空气时 {@code useItemOn}
 *       根本不会被调用，由注入点 ③ 按原版路径触发激流。</li>
 *   <li><b>放置方块优先</b>：不接管方块交互；注入点 ③——只有「准星命中方块 <b>且</b> 副手拿着
 *       可放置的方块」时才跳过主手使用（不触发激流），把这轮右键让给副手去放方块；
 *       右击空气/实体或副手没有可放方块时照常触发激流。</li>
 * </ul>
 *
 * <p><b>副手食物（配置「副手食物」= 优先进食）</b>时：主手不发三叉戟的使用包，进食完全交给原版流程
 * （原因见 {@link ToolInteractionHandler#shouldSkipMainHandUse}）；「工具优先」下同时让开方块交互，
 * 其余模式下可交互方块仍会正常交互。
 *
 * <p>还有一步必要修补（注入点 ④）：{@code TridentItem.use()} 在「有激流附魔但不在水中/雨中」时
 * 提前返回 {@code FAIL}，不会进入使用状态，而松开右键的 RELEASE_USE_ITEM 包只在该状态下才发出——
 * 服务端因此收不到释放动作（陆地要能放激流，服务端必须有对应实现）。所以在使用失败后补一次
 * {@code startUsingItem}，让陆地也能正常把释放动作发出去。
 *
 * <p>纯客户端实现，服务器无需安装本模组。
 */
@Mixin(value = MultiPlayerGameMode.class, remap = false)
public class ToolInteractionMixin {

    /** ① 方块交互入口：「工具优先」在这里接管。 */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void satella$onUseItemOnHead(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (player == null || !ToolInteractionHandler.isToolFirst()) {
            // 「可交互方块优先 / 放置方块优先」：交给原版处理方块，
            // 方块没接管时由下面第二处注入按模式改用工具
            return;
        }

        // 工具优先 + 优先进食：让开方块交互，由原版流程去吃副手食物（不误开方块、也不放激流）
        if (ToolInteractionHandler.isEatFirstActive(player)) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        ToolInteractionHandler.Decision decision;
        try {
            decision = ToolInteractionHandler.decideTool(player, hand);
        } catch (Throwable t) {
            AutoTrade.LOGGER.warn("工具优先判定异常", t);
            return;
        }

        if (!decision.handled()) {
            return;
        }

        AutoTrade.diagToolFirst(player, hand, decision.selfInvoke());

        MultiPlayerGameMode self = (MultiPlayerGameMode) (Object) this;
        if (decision.selfInvoke()) {
            // 激流三叉戟：自己发包，再返回 SUCCESS 让原版走「挥手并结束」分支
            self.useItem(player, hand);
            cir.setReturnValue(InteractionResult.SUCCESS);
        } else {
            // 弩 / 弓：返回 PASS，由原版回退到 useItem（单次调用）
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /**
     * ② 方块交互之后：方块没有消耗掉这次右键 → 说明是「不可交互方块」，按模式改用工具。
     *
     * <p>只有「可交互方块优先」在这里动手：
     * <ul>
     *   <li>原版返回 {@code PASS}（石头、泥土…）时，自己调用 {@code useItem} 并返回 {@code SUCCESS}，
     *       让激流一定触发，而不是靠原版回退 —— 副手因此也不会抢先把方块放下去。</li>
     *   <li>原版返回 {@code FAIL}（方块特性未启用、超出世界边界…）时，原版 {@code startUseItem}
     *       会直接中断整轮循环，什么都不做；这里同样改成用工具，避免「完全没反应」。</li>
     *   <li>方块返回 {@code SUCCESS}（开箱子、开门、放方块…）时不动它；<b>优先进食</b>生效时
     *       也不动，让原版继续走到副手食物。</li>
     * </ul>
     */
    @Inject(method = "useItemOn", at = @At("RETURN"), cancellable = true, remap = false)
    private void satella$onUseItemOnReturn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (player == null || hand != InteractionHand.MAIN_HAND || cir.isCancelled()) {
            return;
        }
        InteractionResult result = cir.getReturnValue();
        if (result != null && result.consumesAction()) {
            return;
        }
        if (!ToolInteractionHandler.isInteractableFirst()) {
            return;
        }
        if (ToolInteractionHandler.isEatFirstActive(player)) {
            return;
        }

        ToolInteractionHandler.Decision decision;
        try {
            decision = ToolInteractionHandler.decideTool(player, hand);
        } catch (Throwable t) {
            AutoTrade.LOGGER.warn("可交互方块优先判定异常", t);
            return;
        }
        if (!decision.handled()) {
            return;
        }

        AutoTrade.diagToolFirst(player, hand, decision.selfInvoke());

        MultiPlayerGameMode self = (MultiPlayerGameMode) (Object) this;
        if (decision.selfInvoke()) {
            self.useItem(player, hand);
            cir.setReturnValue(InteractionResult.SUCCESS);
        } else {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /** ③ 主手物品使用入口：优先进食 / 放置方块优先且有方块可放时跳过三叉戟。 */
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void satella$skipMainHandUse(Player player, InteractionHand hand,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (player == null || hand != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!ToolInteractionHandler.shouldSkipMainHandUse(player)) {
            return;
        }
        cir.setReturnValue(InteractionResult.PASS);
    }

    /** ④ 物品使用之后：记录副手进食，并补上原版在陆地跳过的那一步。 */
    @Inject(method = "useItem", at = @At("RETURN"), remap = false)
    private void satella$afterItemUse(Player player, InteractionHand hand,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        if (player == null) {
            return;
        }

        InteractionResult result = cir.getReturnValue();

        // 副手食物被原版成功使用 → 记住「本次按住已经吃过」：
        // 覆盖「刚好吃完最后一个、副手已空」的情况，否则下一次右键会立刻落回激流
        if (hand == InteractionHand.OFF_HAND
                && result instanceof InteractionResult.Success
                && ToolInteractionHandler.isFood(player.getItemInHand(hand))) {
            OffhandFoodState.markAte();
        }

        // 陆地上原版跳过的那一步：有激流附魔但不在水中时 use() 提前返回 FAIL，
        // 不会进入使用状态，松开右键也就发不出 RELEASE_USE_ITEM
        if (!(result instanceof InteractionResult.Fail) || player.isUsingItem()) {
            return;
        }
        if (!ToolInteractionHandler.isRiptideTrident(player, player.getItemInHand(hand))) {
            return;
        }
        // 优先进食生效中：主手不占用「使用中」状态，好让原版继续处理副手食物
        if (ToolInteractionHandler.isEatFirstActive(player)) {
            return;
        }
        player.startUsingItem(hand);
    }
}
