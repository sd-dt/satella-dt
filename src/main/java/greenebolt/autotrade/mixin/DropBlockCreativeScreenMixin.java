package greenebolt.autotrade.mixin;

import greenebolt.autotrade.DropBlock;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 创造模式物品栏内丢弃（THROW 点击在创造界面走独立路径） */
@Mixin(value = CreativeModeInventoryScreen.class, remap = false)
public class DropBlockCreativeScreenMixin {
    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void satella$blockThrow(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		if (input != ContainerInput.THROW || DropBlock.suppressInternal) {
			return;
		}
		// 仅拦截丢具体槽位（创造界面内按 Q 等）；slot == null 是光标移出界面丢光标物品，放行
		if (slot == null) {
			return;
		}
		ItemStack stack = slot.getItem();
		if (!stack.isEmpty() && DropBlock.isBlockedItem(stack.getItem())) {
			ci.cancel();
		}
    }
}
