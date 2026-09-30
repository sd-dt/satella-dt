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
 * 三叉戟交互的三种模式 + 副手食物，共三处注入。
 *
 * <p>原版 {@code Minecraft#startUseItem} 按「主手方块 → 主手物品 → 副手方块 → 副手物品」循环，
 * 每只手先 {@code useItemOn}、不成功再 {@code useItem}，返回值 {@code Success} 会直接结束本次右键：
 * <pre>
 *   for (InteractionHand hand : InteractionHand.values()) {
 *       InteractionResult r = gameMode.useItemOn(player, hand, hit);
 *       if (r instanceof Success) { 挥手; return; }
 *       if (r instanceof Fail) return;
 *       r = gameMode.useItem(player, hand);
 *       if (r instanceof Success) { 挥手; return; }
 *   }
 * </pre>
 *
 * <p>三种模式（配置「三叉戟交互」）分别改写这条链的不同环节：
 * <ul>
 *   <li><b>工具优先</b>：{@code useItemOn} 入口直接接管——返回 {@code SUCCESS} 让原版结束本次右键。
 *       激流三叉戟自己补一次 {@code useItem}（原版回退会因陆地 {@code use()} 返回 FAIL 而中断），
 *       弩/弓返回 {@code PASS} 由原版单次回退调用。</li>
 *   <li><b>可交互方块优先</b>：不在入口接管。可交互方块由原版交互成功并结束；不可交互方块原版返回
 *       {@code PASS}，自然回退到 {@code useItem}（即触发激流）。</li>
 *   <li><b>放置方块优先</b>：入口也不接管；主手的物品使用被下面第二处注入跳过（不触发激流），
 *       于是循环继续到副手，由副手把方块放下去。</li>
 * </ul>
 *
 * <p><b>副手食物（配置「副手食物」= 优先进食）</b>时：主手不发三叉戟的使用包，进食完全交给原版流程
 * （原因见 {@link ToolInteractionHandler#shouldSkipMainHandUse}）；「工具优先」下同时让开方块交互，
 * 其余模式下可交互方块仍会正常交互。
 *
 * <p>还有一步必要修补：{@code TridentItem.use()} 在「有激流附魔但不在水中/雨中」时提前返回 {@code FAIL}，
 * 不会进入使用状态，而松开右键的 RELEASE_USE_ITEM 包只在该状态下才发出——服务端因此收不到释放动作。
 * 所以在使用失败后补一次 {@code startUsingItem}，陆地才能正常放激流。
 *
 * <p>拦截不依赖方块交互距离：{@code useItemOn} 只在准星确实命中方块时被调用。
 * 纯客户端实现，服务器无需安装本模组。
 */
@Mixin(value = MultiPlayerGameMode.class, remap = false)
public class ToolInteractionMixin {

    /** ① 方块交互入口：按模式决定是否接管。 */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void satella$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        if (player == null || !ToolInteractionHandler.isToolFirst()) {
            // 「可交互方块优先 / 放置方块优先」：交给原版处理方块，
            // 不可交互时原版会回退到 useItem（是否需要跳过由第二处注入判断）
            return;
        }

        // 工具优先 + 优先进食：让开方块交互，由原版流程去吃副手食物（不误开方块、也不放激流）
        if (ToolInteractionHandler.isEatFirstActive(player)) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }

        ToolInteractionHandler.Decision decision;
        try {
            decision = ToolInteractionHandler.decide(player, hand);
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

    /** ② 主手物品使用入口：优先进食 / 放置方块优先时跳过三叉戟。 */
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

    /** ③ 物品使用之后：记录副手进食，并补上原版在陆地跳过的那一步。 */
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
