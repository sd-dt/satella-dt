package greenebolt.autotrade.mixin.tweakeroo;

import greenebolt.autotrade.StackNormalizer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tweakeroo 的「自动补货」堆叠比较改用「潜影盒归一化」结果（移植自 1.21.11 主版）。
 *
 * <p>26.2 不混淆，所以 redirect 目标直接写官方名描述符（1.21.11 侧因 remap 写的是
 * intermediary 的 {@code class_1799}）。Tweakeroo 未安装时由
 * {@code ShulkerCompatMixinPlugin} 整条跳过；若该模组版本变化导致注入点不存在，
 * {@code require = 0} 只记日志不崩游戏。
 */
@Mixin(targets = "fi.dy.masa.tweakeroo.util.InventoryUtils", remap = false)
public abstract class InventoryUtilsMixin {
    @Redirect(
            method = {"preRestockHand", "findSlotWithItem"},
            at = @At(
                    value = "INVOKE",
                    target = "Lfi/dy/masa/malilib/util/InventoryUtils;areStacksEqualIgnoreDurability(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
                    remap = false),
            require = 0,
            remap = false)
    private static boolean autoTrade$compareRestockStacks(ItemStack stack, ItemStack reference) {
        if (StackNormalizer.sameIdentity(stack, reference)) {
            return true;
        }
        return areStacksEqualIgnoreDurability(stack, reference);
    }

    private static boolean areStacksEqualIgnoreDurability(ItemStack first, ItemStack second) {
        if (first == null || second == null) {
            return false;
        }
        ItemStack firstCopy = first.copy();
        ItemStack secondCopy = second.copy();
        firstCopy.setCount(1);
        secondCopy.setCount(1);
        if (firstCopy.isDamaged() && firstCopy.isDamageableItem()) {
            firstCopy.setDamageValue(0);
        }
        if (secondCopy.isDamaged() && secondCopy.isDamageableItem()) {
            secondCopy.setDamageValue(0);
        }
        return ItemStack.matches(firstCopy, secondCopy);
    }
}
