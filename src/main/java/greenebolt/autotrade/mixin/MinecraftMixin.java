package greenebolt.autotrade.mixin;

import greenebolt.autotrade.AutoTrade;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, remap = false)
public class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void autoTrade$tick(CallbackInfo ci) {
        AutoTrade.tick((Minecraft) (Object) this);
    }
}
