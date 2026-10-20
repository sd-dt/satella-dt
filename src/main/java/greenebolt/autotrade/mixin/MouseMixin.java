package greenebolt.autotrade.mixin;

import greenebolt.autotrade.AutoTrade;
import greenebolt.autotrade.OffhandFoodState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 跟踪真实鼠标右键的按下状态，供「更NB的弩」判断玩家是否在按住右键，
 * 并在松开时结束「优先进食」的本次进食会话（26.3 官方名版本：注入点仍是
 * {@code MouseHandler#onButton(long, MouseButtonInfo, int)}）。
 *
 * <p>26.3 起原版从 GLFW 换到 SDL：{@code org.lwjgl.glfw} 已不在依赖里，
 * action 由 {@code SDLEventHandler} 映射为「按下=1 / 松开=0」（与 GLFW 常量同值），
 * 因此这里用本地常量 0 判断松开，不再引用 GLFW。
 *
 * <p>用物理按键而非使用键本身，是因为使用键会被本功能自己模拟按下，
 * 用物理状态才能区分「玩家真的按着」和「我们模拟按下」。
 */
@Mixin(value = MouseHandler.class, remap = false)
public abstract class MouseMixin {
    /** SDLEventHandler: SDL_MOUSEBUTTONUP(1026) → 0；SDL_MOUSEBUTTONDOWN(1025) → 1。 */
    private static final int ACTION_RELEASE = 0;

    @Inject(method = "onButton", at = @At("HEAD"), remap = false)
    private void autoTrade$trackRightMouse(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        if (input.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            boolean pressed = action != ACTION_RELEASE;
            AutoTrade.updatePhysicalUseKeyState(pressed);
            OffhandFoodState.onRightButton(pressed);
        }
    }
}
