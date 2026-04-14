package io.github.notzorba.notmines.leaderboard;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.github.notzorba.notmines.NotMinesPlugin;
import io.github.notzorba.notmines.economy.EconomyBridge;
import io.github.notzorba.notmines.gui.GuiConfig;
import io.github.notzorba.notmines.gui.LeaderboardGuiConfig;
import io.github.notzorba.notmines.stats.LeaderboardPage;
import io.github.notzorba.notmines.stats.LeaderboardStat;
import io.github.notzorba.notmines.stats.StatsService;
import io.github.notzorba.notmines.util.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class LeaderboardManager {
    private static final LeaderboardStat DEFAULT_STAT = LeaderboardStat.NET_PROFIT;

    private final NotMinesPlugin plugin;
    private final StatsService statsService;
    private final EconomyBridge economy;
    private MessageService messages;
    private GuiConfig guiConfig;

    public LeaderboardManager(
        final NotMinesPlugin plugin,
        final StatsService statsService,
        final MessageService messages,
        final GuiConfig guiConfig,
        final EconomyBridge economy
    ) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.messages = messages;
        this.guiConfig = guiConfig;
        this.economy = economy;
    }

    public void openLeaderboard(final Player player) {
        final ProfileTextures textures = this.captureProfileTextures(player);
        final LeaderboardInventoryHolder holder = new LeaderboardInventoryHolder(
            player.getUniqueId(),
            player.getName(),
            textures.texture(),
            textures.signature(),
            DEFAULT_STAT
        );
        final Inventory inventory = LeaderboardMenu.createInventory(holder, this.messages, this.leaderboardGui());
        player.openInventory(inventory);
        this.loadPage(holder);
    }

    public void handleInventoryClick(final Player player, final LeaderboardInventoryHolder holder, final int rawSlot) {
        if (!holder.viewerId().equals(player.getUniqueId())) {
            return;
        }

        final var layout = this.leaderboardGui().layout();
        if (rawSlot == layout.closeSlot()) {
            player.closeInventory();
            return;
        }

        if (rawSlot == layout.filterSlot()) {
            holder.setStat(holder.stat().next());
            holder.setPage(0);
            this.loadPage(holder);
            return;
        }

        if (rawSlot == layout.previousPageSlot()) {
            holder.setPage(holder.page() - 1);
            this.loadPage(holder);
            return;
        }

        if (rawSlot == layout.nextPageSlot()) {
            holder.setPage(holder.page() + 1);
            this.loadPage(holder);
        }
    }

    public void reloadRuntimeResources(final MessageService messages, final GuiConfig guiConfig) {
        this.messages = messages;
        this.guiConfig = guiConfig;
    }

    private void loadPage(final LeaderboardInventoryHolder holder) {
        LeaderboardMenu.renderLoading(holder, this.messages, this.leaderboardGui());
        final int requestVersion = holder.nextRequestVersion();
        final int pageSize = this.leaderboardGui().layout().entrySlots().length;
        this.statsService.getLeaderboardAsync(holder.stat(), holder.page(), pageSize, holder.viewerId()).whenComplete((page, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("Failed to load the NotMines leaderboard for " + holder.viewerId() + ".");
                return;
            }

            if (!this.plugin.isEnabled()) {
                return;
            }

            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.renderIfCurrent(holder, requestVersion, page));
        });
    }

    private void renderIfCurrent(
        final LeaderboardInventoryHolder holder,
        final int requestVersion,
        final LeaderboardPage page
    ) {
        if (!holder.isRequestCurrent(requestVersion)) {
            return;
        }

        holder.setStat(page.stat());
        holder.setPage(page.page());
        LeaderboardMenu.renderPage(holder, page, this.messages, this.leaderboardGui(), this.economy);
    }

    private LeaderboardGuiConfig leaderboardGui() {
        return this.guiConfig.leaderboard();
    }

    private ProfileTextures captureProfileTextures(final Player player) {
        final ProfileProperty property = player.getPlayerProfile().getProperties().stream()
            .filter(candidate -> "textures".equalsIgnoreCase(candidate.getName()))
            .findFirst()
            .orElse(null);
        if (property == null) {
            return new ProfileTextures(null, null);
        }

        return new ProfileTextures(property.getValue(), property.getSignature());
    }

    private record ProfileTextures(String texture, String signature) {
    }
}
