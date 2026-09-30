package greenebolt.autotrade;

import fi.dy.masa.itemscroller.recipes.RecipePattern;
import fi.dy.masa.itemscroller.recipes.RecipeStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.Objects;

public final class ResidualCrafting {
    private ResidualCrafting() {}

    public static boolean craft(AbstractContainerMenu menu, Minecraft minecraft, int firstGridSlot, int lastGridSlot, int iterations) {
        if (minecraft.player == null || minecraft.gameMode == null) return false;
        RecipePattern recipe = RecipeStorage.getInstance().getSelectedRecipe();
        ItemStack result = recipe.getResult();
        ItemStack[] ingredients = recipe.getRecipeItems();
        if (result.isEmpty() || ingredients.length > lastGridSlot - firstGridSlot + 1) {
            resetRecipeBookState();
            return false;
        }
        if (AutoTradeConfigs.Trade.CRAFT_FILL_MODE.getOptionListValue() == CraftFillMode.RECIPE_BOOK) {
            return craftWithRecipeBook(menu, minecraft, recipe, result);
        }
        resetRecipeBookState();
        boolean crafted = false;
        int limit = Math.max(1, iterations) * 1024;
        for (int iteration = 0; iteration < limit; iteration++) {
            if (!prepare(menu, minecraft, firstGridSlot, lastGridSlot, ingredients)) return crafted;
            ItemStack output = menu.getSlot(0).getItem();
            if (!ItemStack.isSameItemSameComponents(output, result)) return crafted;
            ItemStack[] gridBefore = copyGrid(menu, firstGridSlot, lastGridSlot);
            boolean previousSuppress = DropBlock.suppressInternal;
            DropBlock.suppressInternal = true;
            try {
                // 与 Item Scroller 的 dropStack 相同：button=1，尽可能取出输出槽中的整组结果。
                click(minecraft, menu, 0, 1, ContainerInput.THROW);
            } finally {
                DropBlock.suppressInternal = previousSuppress;
            }
            crafted = true;
            if (!gridChanged(menu, firstGridSlot, lastGridSlot, gridBefore)) return crafted;
        }
        return crafted;
    }

    private static AbstractContainerMenu recipeBookMenu;
    private static RecipeDisplayId recipeBookId;
    private static ItemStack recipeBookResult;

    private static boolean craftWithRecipeBook(AbstractContainerMenu menu, Minecraft minecraft,
                                                RecipePattern recipe, ItemStack result) {
        recipe.storeIdFromClientRecipeBook(minecraft);
        RecipeDisplayId recipeId = recipe.getNetworkRecipeId();
        if (recipeId == null) {
            resetRecipeBookState();
            return false;
        }

        boolean changed = recipeBookMenu != menu
                || !Objects.equals(recipeBookId, recipeId)
                || recipeBookResult == null
                || !ItemStack.isSameItemSameComponents(recipeBookResult, result);
        if (changed) {
            recipeBookMenu = menu;
            recipeBookId = recipeId;
            recipeBookResult = result.copy();
        }

        ItemStack output = menu.getSlot(0).getItem();
        if (!output.isEmpty()) {
            if (ItemStack.isSameItemSameComponents(output, recipeBookResult)) {
                throwRecipeBookOutput(menu, minecraft);
            }
            return false;
        }

        int requests = AutoTradeConfigs.Trade.RECIPE_FILL_ITERATIONS.getIntegerValue();
        // 与“配方填入次数”的配置含义一致：本周期连续发送指定次数的普通请求。
        for (int i = 0; i < requests; i++) {
            minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipeId, false);
        }
        return false;
    }

    private static void throwRecipeBookOutput(AbstractContainerMenu menu, Minecraft minecraft) {
        boolean previousSuppress = DropBlock.suppressInternal;
        DropBlock.suppressInternal = true;
        try {
            click(minecraft, menu, 0, 1, ContainerInput.THROW);
        } finally {
            DropBlock.suppressInternal = previousSuppress;
        }
        if (!menu.getCarried().isEmpty()) {
            returnCursorToInventoryOrDrop(menu, 1, 9, minecraft);
        }
    }

    private static void resetRecipeBookState() {
        recipeBookMenu = null;
        recipeBookId = null;
        recipeBookResult = null;
    }

    private static boolean prepare(AbstractContainerMenu menu, Minecraft minecraft, int first, int last, ItemStack[] ingredients) {
        recoverCursor(menu, first, last, ingredients, minecraft);
        repairMalformedGrid(menu, first, last, ingredients, minecraft);
        recoverCursor(menu, first, last, ingredients, minecraft);
        // 与 1.21.11 行为一致：一圈未放入材料也继续摊匀与清理，最后按配方是否满足判定本轮成败。
        fillIngredient(menu, minecraft, first, last, ingredients);
        balanceGrid(menu, minecraft, first, ingredients);
        recoverCursor(menu, first, last, ingredients, minecraft);
        for (int index = ingredients.length; first + index <= last; index++) {
            if (!menu.getSlot(first + index).getItem().isEmpty()) {
                moveToInventory(menu, minecraft, first + index, first, last);
            }
        }
        recoverCursor(menu, first, last, ingredients, minecraft);
        return menu.getCarried().isEmpty() && gridMatchesRecipe(menu, first, last, ingredients);
    }

    private static void repairMalformedGrid(AbstractContainerMenu menu, int first, int last,
                                             ItemStack[] ingredients, Minecraft minecraft) {
        for (int left = 0; left < ingredients.length && first + left <= last; left++) {
            int leftSlot = first + left;
            ItemStack leftStack = menu.getSlot(leftSlot).getItem();
            if (leftStack.isEmpty() || ItemStack.isSameItemSameComponents(leftStack, ingredients[left])) continue;
            for (int right = left + 1; right < ingredients.length && first + right <= last; right++) {
                int rightSlot = first + right;
                ItemStack rightStack = menu.getSlot(rightSlot).getItem();
                if (!rightStack.isEmpty()
                        && ItemStack.isSameItemSameComponents(leftStack, ingredients[right])
                        && ItemStack.isSameItemSameComponents(rightStack, ingredients[left])) {
                    click(minecraft, menu, leftSlot, 0, ContainerInput.PICKUP);
                    click(minecraft, menu, rightSlot, 0, ContainerInput.PICKUP);
                    click(minecraft, menu, leftSlot, 0, ContainerInput.PICKUP);
                    break;
                }
            }
        }
        for (int index = 0; index < ingredients.length && first + index <= last; index++) {
            int sourceSlot = first + index;
            ItemStack source = menu.getSlot(sourceSlot).getItem();
            if (source.isEmpty() || ItemStack.isSameItemSameComponents(source, ingredients[index])) continue;
            for (int targetIndex = 0; targetIndex < ingredients.length; targetIndex++) {
                int targetSlot = first + targetIndex;
                if (!ingredients[targetIndex].isEmpty() && menu.getSlot(targetSlot).getItem().isEmpty()
                        && ItemStack.isSameItemSameComponents(source, ingredients[targetIndex])) {
                    click(minecraft, menu, sourceSlot, 0, ContainerInput.PICKUP);
                    click(minecraft, menu, targetSlot, 0, ContainerInput.PICKUP);
                    recoverCursor(menu, first, last, ingredients, minecraft);
                    break;
                }
            }
        }
        for (int index = 0; index < ingredients.length && first + index <= last; index++) {
            int slot = first + index;
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, ingredients[index])) {
                click(minecraft, menu, slot, 0, ContainerInput.PICKUP);
                returnCursorToInventoryOrDrop(menu, first, last, minecraft);
            }
        }
    }

    private static boolean gridMatchesRecipe(AbstractContainerMenu menu, int first, int last, ItemStack[] ingredients) {
        for (int index = 0; index < ingredients.length; index++) {
            int slot = first + index;
            if (slot > last) return false;
            ItemStack expected = ingredients[index];
            ItemStack actual = menu.getSlot(slot).getItem();
            if (expected.isEmpty() ? !actual.isEmpty() : !ItemStack.isSameItemSameComponents(actual, expected)) return false;
        }
        return true;
    }

    private static void recoverCursor(AbstractContainerMenu menu, int first, int last,
                                      ItemStack[] ingredients, Minecraft minecraft) {
        if (menu.getCarried().isEmpty()) return;
        for (int index = 0; index < ingredients.length && first + index <= last; index++) {
            if (!ingredients[index].isEmpty() && menu.getSlot(first + index).getItem().isEmpty()
                    && ItemStack.isSameItemSameComponents(menu.getCarried(), ingredients[index])) {
                click(minecraft, menu, first + index, 0, ContainerInput.PICKUP);
                break;
            }
        }
        if (!menu.getCarried().isEmpty()) returnCursorToInventoryOrDrop(menu, first, last, minecraft);
    }

    private static void returnCursorToInventoryOrDrop(AbstractContainerMenu menu, int first, int last,
                                                       Minecraft minecraft) {
        if (menu.getCarried().isEmpty()) return;
        for (int slot = last + 1; slot < menu.slots.size() && !menu.getCarried().isEmpty(); slot++) {
            ItemStack target = menu.getSlot(slot).getItem();
            ItemStack carried = menu.getCarried();
            if (!target.isEmpty() && (!ItemStack.isSameItemSameComponents(target, carried)
                    || target.getCount() >= target.getMaxStackSize())) continue;
            click(minecraft, menu, slot, 0, ContainerInput.PICKUP);
        }
        if (!menu.getCarried().isEmpty()) {
            boolean previous = DropBlock.suppressInternal;
            DropBlock.suppressInternal = true;
            try {
                click(minecraft, menu, -999, 0, ContainerInput.THROW);
            } finally {
                DropBlock.suppressInternal = previous;
            }
        }
    }

    private static AbstractContainerMenu scanMenu;
    private static ItemStack[] scanRecipe;
    private static int scanCursor = -1;

    /** Scans the inventory once with one shared cursor for every recipe ingredient. */
    private static void fillIngredient(AbstractContainerMenu menu, Minecraft minecraft, int first, int last,
                                       ItemStack[] ingredients) {
        prepareScanState(menu, ingredients);

        int slotCount = menu.slots.size();
        if (slotCount == 0) {
            return;
        }

        // 建材料表：每格记录还差多少个才补满（M = 材料最大堆叠上限 - 格内已有数量）
        int[] deficit = new int[ingredients.length];
        for (int recipeIndex = 0; recipeIndex < ingredients.length; recipeIndex++) {
            int slot = first + recipeIndex;
            ItemStack gridStack = slot <= last ? menu.getSlot(slot).getItem() : ItemStack.EMPTY;
            int present = !ingredients[recipeIndex].isEmpty()
                    && ItemStack.isSameItemSameComponents(gridStack, ingredients[recipeIndex])
                    ? gridStack.getCount() : 0;
            deficit[recipeIndex] = ingredients[recipeIndex].isEmpty() || slot > last
                    ? 0 : ingredients[recipeIndex].getMaxStackSize() - present;
        }

        int start = (scanCursor + 1 + slotCount) % slotCount;
        for (int offset = 0; offset < slotCount; offset++) {
            int source = (start + offset) % slotCount;
            scanCursor = source;
            if (tableComplete(deficit)) {
                // 材料补满提前结束时，把游标回退到最后一个实际检查过的槽位，
                // 让当前这个没检查过的槽位下一轮第一个被查，避免被跳过
                scanCursor = (source - 1 + slotCount) % slotCount;
                return;
            }
            if (source >= first && source <= last) {
                continue;
            }

            ItemStack sourceStack = menu.getSlot(source).getItem();
            if (sourceStack.isEmpty()) {
                continue;
            }
            int residue = AutoTradeConfigs.Trade.CRAFT_RESIDUE.getIntegerValue();
            if (sourceStack.getCount() < 2 * residue) {
                continue;
            }

            // 查材料表：这堆物品要补哪个合成格
            int target = -1;
            for (int recipeIndex = 0; recipeIndex < ingredients.length; recipeIndex++) {
                if (deficit[recipeIndex] <= 0
                        || !ItemStack.isSameItemSameComponents(sourceStack, ingredients[recipeIndex])) {
                    continue;
                }
                ItemStack gridStack = menu.getSlot(first + recipeIndex).getItem();
                if (!gridStack.isEmpty() && !ItemStack.isSameItemSameComponents(gridStack, ingredients[recipeIndex])) {
                    continue;
                }
                target = recipeIndex;
                break;
            }
            if (target < 0) {
                continue;
            }

            // 只有当物品堆的一半不超过该格缺口时才取，保证取出来刚好补得进格子，不会有多余
            int half = (sourceStack.getCount() + 1) / 2;
            if (half > deficit[target]) {
                continue;
            }

            // 右键拾取一半到光标；拾到多少就在材料表里扣多少，扣到 0 以下才认定该格补满
            click(minecraft, menu, source, 1, ContainerInput.PICKUP);
            int picked = menu.getCarried().getCount();
            if (picked == 0 || !ItemStack.isSameItemSameComponents(menu.getCarried(), ingredients[target])) {
                returnCursorToInventoryOrDrop(menu, first, last, minecraft);
                continue;
            }
            deficit[target] -= picked;
            // 左键把光标物品放进目标合成格
            click(minecraft, menu, first + target, 0, ContainerInput.PICKUP);

            // 光标还有剩余（例如本地与服务器状态不一致）：进背包，背包放不下才丢弃
            if (!menu.getCarried().isEmpty()) {
                returnCursorToInventoryOrDrop(menu, first, last, minecraft);
            }
        }
    }

    /** 材料表里所有格子的缺口都已扣减到 0 以下，即补料完成 */
    private static boolean tableComplete(int[] deficit) {
        for (int value : deficit) {
            if (value > 0) {
                return false;
            }
        }
        return true;
    }

    private static void prepareScanState(AbstractContainerMenu menu, ItemStack[] ingredients) {
        if (scanMenu == menu
                && sameRecipe(scanRecipe, ingredients)
                && scanCursor < menu.slots.size()) {
            return;
        }
        scanMenu = menu;
        scanRecipe = copyRecipe(ingredients);
        scanCursor = menu.slots.size() - 1;
    }

    private static boolean sameRecipe(ItemStack[] first, ItemStack[] second) {
        if (first == null || second == null || first.length != second.length) {
            return false;
        }
        for (int index = 0; index < first.length; index++) {
            if (!ItemStack.isSameItemSameComponents(first[index], second[index])) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack[] copyRecipe(ItemStack[] ingredients) {
        ItemStack[] copy = new ItemStack[ingredients.length];
        for (int index = 0; index < ingredients.length; index++) {
            copy[index] = ingredients[index].copy();
        }
        return copy;
    }

    /** Evens out each ingredient's grid slots so the recipe stays valid and balanced. */
    private static void balanceGrid(AbstractContainerMenu menu, Minecraft minecraft, int first, ItemStack[] ingredients) {
        for (int recipeIndex = 0; recipeIndex < ingredients.length; recipeIndex++) {
            ItemStack ingredient = ingredients[recipeIndex];
            if (ingredient.isEmpty() || wasHandled(ingredients, recipeIndex)) continue;

            int slotCount = 0;
            for (int index = 0; index < ingredients.length; index++) {
                if (ItemStack.isSameItemSameComponents(ingredient, ingredients[index])) slotCount++;
            }
            int[] slots = new int[slotCount];
            for (int index = 0, s = 0; index < ingredients.length; index++) {
                if (ItemStack.isSameItemSameComponents(ingredient, ingredients[index])) slots[s++] = first + index;
            }
            if (slots.length < 2) continue;

            while (true) {
                int maxIdx = 0;
                int minIdx = 0;
                int total = 0;
                for (int i = 0; i < slots.length; i++) {
                    ItemStack stack = menu.getSlot(slots[i]).getItem();
                    int count = ItemStack.isSameItemSameComponents(stack, ingredient) ? stack.getCount() : 0;
                    total += count;
                    if (count > countAt(menu, slots, ingredient, maxIdx)) maxIdx = i;
                    if (count < countAt(menu, slots, ingredient, minIdx)) minIdx = i;
                }
                if (total < slots.length) break; // cannot give every slot at least 1
                if (countAt(menu, slots, ingredient, maxIdx) - countAt(menu, slots, ingredient, minIdx) <= 1) break;

                // Move one item from the fullest slot to the emptiest slot.
                click(minecraft, menu, slots[maxIdx], 0, ContainerInput.PICKUP);
                click(minecraft, menu, slots[maxIdx], 1, ContainerInput.PICKUP);
                click(minecraft, menu, slots[minIdx], 1, ContainerInput.PICKUP);
                if (!menu.getCarried().isEmpty()) click(minecraft, menu, slots[maxIdx], 0, ContainerInput.PICKUP);
                if (!menu.getCarried().isEmpty()) return;
            }
        }
    }

    private static int countAt(AbstractContainerMenu menu, int[] slots, ItemStack ingredient, int index) {
        ItemStack stack = menu.getSlot(slots[index]).getItem();
        return ItemStack.isSameItemSameComponents(stack, ingredient) ? stack.getCount() : 0;
    }

    private static boolean wasHandled(ItemStack[] ingredients, int index) {
        for (int previous = 0; previous < index; previous++) {
            if (ItemStack.isSameItemSameComponents(ingredients[index], ingredients[previous])) return true;
        }
        return false;
    }

    private static boolean moveToInventory(AbstractContainerMenu menu, Minecraft minecraft, int source, int first, int last) {
        click(minecraft, menu, source, 0, ContainerInput.PICKUP);
        returnCursorToInventoryOrDrop(menu, first, last, minecraft);
        return menu.getCarried().isEmpty();
    }

    private static ItemStack[] copyGrid(AbstractContainerMenu menu, int first, int last) {
        ItemStack[] grid = new ItemStack[last - first + 1];
        for (int index = 0; index < grid.length; index++) {
            grid[index] = menu.getSlot(first + index).getItem().copy();
        }
        return grid;
    }

    private static boolean gridChanged(AbstractContainerMenu menu, int first, int last, ItemStack[] before) {
        if (before.length != last - first + 1) return true;
        for (int index = 0; index < before.length; index++) {
            ItemStack previous = before[index];
            ItemStack after = menu.getSlot(first + index).getItem();
            if (!ItemStack.isSameItemSameComponents(previous, after)
                    || previous.getCount() != after.getCount()) return true;
        }
        return false;
    }

    private static void click(Minecraft minecraft, AbstractContainerMenu menu, int slot, int button, ContainerInput action) {
        Player player = minecraft.player;
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, action, player);
    }
}
