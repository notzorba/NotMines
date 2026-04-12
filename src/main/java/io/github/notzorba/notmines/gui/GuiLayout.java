package io.github.notzorba.notmines.gui;

public record GuiLayout(
    int inventorySize,
    int[] boardSlots,
    int infoSlot,
    int statsSlot,
    int betSlot,
    int cashoutSlot,
    int oddsSlot,
    int helpSlot
) {
}
