package greenebolt.autotrade.mixin;

import greenebolt.autotrade.AutoTrade;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class, remap = false)
public class EntityGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true, remap = false)
    private void autoTrade$highlightTrackedVillager(CallbackInfoReturnable<Boolean> cir) {
        if (AutoTrade.isHighlighted((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
