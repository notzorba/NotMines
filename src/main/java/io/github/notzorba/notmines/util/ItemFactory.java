package io.github.notzorba.notmines.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemFactory {
    private ItemFactory() {
    }

    public static ItemStack create(
        final Material material,
        final Component displayName,
        final List<Component> lore,
        final ItemFlag... flags
    ) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(normalize(displayName));
        meta.lore(lore.stream().map(ItemFactory::normalize).toList());
        meta.addItemFlags(flags);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack create(final Material material, final Component displayName, final List<Component> lore) {
        return create(material, displayName, lore, new ItemFlag[0]);
    }

    private static Component normalize(final Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
