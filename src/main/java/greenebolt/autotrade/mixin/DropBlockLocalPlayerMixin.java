package greenebolt.autotrade.mixin;

import greenebolt.autotrade.DropBlock;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 手持 Q / Ctrl+Q 丢弃 */
@Mixin(value = LocalPlayer.class, remap = false)
public class DropBlockLocalPlayerMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true, remap = false)
    private void satella$blockDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (DropBlock.isHandDropBlocked(self.getMainHandItem())) {
            cir.setReturnValue(false);
        }
    }
}
