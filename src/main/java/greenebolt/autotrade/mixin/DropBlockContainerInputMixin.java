package greenebolt.autotrade.mixin;

import greenebolt.autotrade.DropBlock;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 容器内 Q / Ctrl+Q、光标移出界面丢弃等（THROW 类点击） */
@Mixin(value = MultiPlayerGameMode.class, remap = false)
public class DropBlockContainerInputMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true, remap = false)
    private void satella$blockThrow(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (player != null && DropBlock.isSlotDropBlocked(player.containerMenu, slotId, input)) {
            ci.cancel();
        }
    }
}
