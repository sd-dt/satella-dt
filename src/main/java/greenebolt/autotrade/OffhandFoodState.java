package greenebolt.autotrade;

/**
 * 本次右键按住期间的进食状态（移植自「更好的激流三叉戟」的 RiptideState）。
 *
 * <p>存在的意义：进食动画结束后玩家往往已经吃饱，此时「副手还有食物」为真但「吃得下」为假。
 * 若只用物品判断，右键会在吃完后立刻落回激流 —— 表现为「吃完就往前飞出」。因此额外记住
 * 「本次按住已经吃过」，只要右键还没松开就继续压制激流。
 *
 * <p>右键松开时（{@code mixin/MouseMixin} 捕获）复位。
 */
public final class OffhandFoodState {

    private static boolean ateThisPress;

    private OffhandFoodState() {}

    /** 右键按下状态变化；松开时结束本次进食会话。 */
    public static void onRightButton(boolean down) {
        if (!down) {
            ateThisPress = false;
        }
    }

    /** 记录「本次按住已经进食」。 */
    public static void markAte() {
        ateThisPress = true;
    }

    /** 本次按住是否已经进食过。 */
    public static boolean ateThisPress() {
        return ateThisPress;
    }
}
