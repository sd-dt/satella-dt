package greenebolt.autotrade.mixin;

import greenebolt.autotrade.AutoTrade;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiPlayerGameMode.class, remap = false)
public class InteractionMixin {
    @Inject(method = "interact", at = @At("HEAD"), remap = false)
    private void autoTrade$track(Player player, Entity entity, EntityHitResult hit, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        AutoTrade.onInteractEntity(entity);
    }
}
