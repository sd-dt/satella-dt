package greenebolt.autotrade.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code Minecraft#startUseItem()}（原版「使用物品」入口，对应 1.21.11 的
 * {@code MinecraftClient#doItemUse()}），供「更NB的弩」做周期性右键。
 * 26.2 里该方法是 private，因此用 {@link Invoker} 访问器而不是 {@code @Shadow}。
 */
@Mixin(value = Minecraft.class, remap = false)
public interface MinecraftInvoker {
    @Invoker("startUseItem")
    void autoTrade$startUseItem();
}
