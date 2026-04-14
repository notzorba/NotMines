package io.github.notzorba.notmines.gui;

public record LeaderboardGuiConfig(
    String title,
    LeaderboardLayout layout,
    GuiItemTemplate filler,
    GuiItemTemplate summary,
    GuiItemTemplate filter,
    GuiItemTemplate loading,
    GuiItemTemplate empty,
    GuiItemTemplate entry,
    GuiItemTemplate focusedEntry,
    GuiItemTemplate previousPage,
    GuiItemTemplate nextPage,
    GuiItemTemplate pageInfo,
    GuiItemTemplate close
) {
}
