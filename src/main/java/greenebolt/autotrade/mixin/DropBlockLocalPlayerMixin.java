package greenebolt.autotrade.mixin;

import greenebolt.autotrade.DropBlock;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手持 Q / Ctrl+Q 丢弃拦截。
 *
 * <p>26.3 把丢弃入口从 {@code LocalPlayer#drop(boolean)} 移到了
 * {@code MultiPlayerGameMode#dropItem(LocalPlayer, boolean)}（{@code Minecraft#handleKeybinds}
 * 现在调后者），因此注入点随之下移；返回值由 boolean 变为 void，拦截方式改为
 * 直接取消整个方法调用。
 */
@Mixin(value = MultiPlayerGameMode.class, remap = false)
public class DropBlockLocalPlayerMixin {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void satella$blockDrop(LocalPlayer player, boolean entireStack, CallbackInfo ci) {
        if (DropBlock.isHandDropBlocked(player.getMainHandItem())) {
            ci.cancel();
        }
    }
}
