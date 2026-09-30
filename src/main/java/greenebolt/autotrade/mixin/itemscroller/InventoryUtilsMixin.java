package greenebolt.autotrade.mixin.itemscroller;

import greenebolt.autotrade.StackNormalizer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item Scroller 的堆叠比较改用「潜影盒归一化」结果（移植自 1.21.11 主版）。
 * 目标类用字符串指定，Item Scroller 未安装时由 {@code ShulkerCompatMixinPlugin} 整条跳过。
 */
@Mixin(targets = "fi.dy.masa.itemscroller.util.InventoryUtils", remap = false)
public abstract class InventoryUtilsMixin {
    @Inject(method = "areStacksEqual", at = @At("HEAD"), cancellable = true, remap = false)
    private static void autoTrade$compareNormalized(ItemStack first, ItemStack second,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (StackNormalizer.sameIdentity(first, second)) {
            cir.setReturnValue(true);
        }
    }
}
