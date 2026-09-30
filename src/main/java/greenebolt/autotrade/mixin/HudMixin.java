package greenebolt.autotrade.mixin;

import greenebolt.autotrade.AutoCraftController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiGraphicsExtractor.class, remap = false)
public class HudMixin {
    @Inject(method = "extractDeferredElements", at = @At("TAIL"), remap = false)
    private void autoTrade$renderAutoCraftingStatus(int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !AutoCraftController.isActive()) {
            return;
        }

        String text = "全自动合成中...";
        GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) (Object) this;
        graphics.centeredText(minecraft.font, text, graphics.guiWidth() / 2,
                graphics.guiHeight() - 42, 0xFF80FF80);
    }
}
