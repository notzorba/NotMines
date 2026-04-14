package io.github.notzorba.notmines.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

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

    public static ItemStack createPlayerHead(
        final Component displayName,
        final List<Component> lore,
        final UUID playerId,
        final String playerName,
        final String skinTexture,
        final String skinTextureSignature,
        final ItemFlag... flags
    ) {
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.displayName(normalize(displayName));
        meta.lore(lore.stream().map(ItemFactory::normalize).toList());
        meta.addItemFlags(flags);
        meta.setPlayerProfile(createProfile(playerId, playerName, skinTexture, skinTextureSignature));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createPlayerHead(
        final Component displayName,
        final List<Component> lore,
        final UUID playerId,
        final String playerName,
        final String skinTexture,
        final String skinTextureSignature
    ) {
        return createPlayerHead(displayName, lore, playerId, playerName, skinTexture, skinTextureSignature, new ItemFlag[0]);
    }

    private static PlayerProfile createProfile(
        final UUID playerId,
        final String playerName,
        final String skinTexture,
        final String skinTextureSignature
    ) {
        final PlayerProfile profile = Bukkit.createProfile(playerId, playerName);
        if (skinTexture != null && !skinTexture.isBlank()) {
            profile.setProperty(new ProfileProperty("textures", skinTexture, blankToNull(skinTextureSignature)));
        }
        return profile;
    }

    private static Component normalize(final Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
