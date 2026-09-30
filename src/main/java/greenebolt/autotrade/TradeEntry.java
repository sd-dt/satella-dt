package greenebolt.autotrade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffer;

public record TradeEntry(Item inputOne, Item inputTwo, Item output) {
    public static TradeEntry build(String first, String second, String output) {
        Item one = ItemNameUtils.parseItem(first);
        Item two = second.trim().isEmpty() ? null : ItemNameUtils.parseItem(second);
        Item result = ItemNameUtils.parseItem(output);
        return one == null || result == null || (!second.trim().isEmpty() && two == null) ? null : new TradeEntry(one, two, result);
    }

    public boolean matches(MerchantOffer offer) {
        if (offer.getResult().getItem() != output || offer.getItemCostA().item().value() != inputOne) return false;
        return inputTwo == null
                ? offer.getItemCostB().isEmpty()
                : offer.getItemCostB().isPresent() && offer.getItemCostB().get().item().value() == inputTwo;
    }
}
