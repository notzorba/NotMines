package io.github.notzorba.notmines.gui;

public record LeaderboardLayout(
    int inventorySize,
    int[] entrySlots,
    int summarySlot,
    int filterSlot,
    int previousPageSlot,
    int nextPageSlot,
    int pageInfoSlot,
    int closeSlot
) {
}
