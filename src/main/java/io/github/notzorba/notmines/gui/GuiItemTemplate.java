package io.github.notzorba.notmines.gui;

import java.util.List;
import org.bukkit.Material;

public record GuiItemTemplate(Material material, String title, List<String> lore) {
}
