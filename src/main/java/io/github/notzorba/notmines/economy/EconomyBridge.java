package io.github.notzorba.notmines.economy;

import io.github.notzorba.notmines.util.Money;
import java.util.Locale;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyBridge {
    private final JavaPlugin plugin;
    private Economy economy;
    private int currencyScale = 2;

    public EconomyBridge(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean refresh() {
        if (this.plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        final RegisteredServiceProvider<Economy> registration = this.plugin.getServer()
            .getServicesManager()
            .getRegistration(Economy.class);
        if (registration == null) {
            return false;
        }

        final Economy provider = registration.getProvider();
        if (provider == null) {
            return false;
        }

        this.economy = provider;

        final int fractionalDigits = provider.fractionalDigits();
        if (fractionalDigits >= 0 && fractionalDigits <= 4) {
            this.currencyScale = fractionalDigits;
        }

        return true;
    }

    public int currencyScale() {
        return this.currencyScale;
    }

    public boolean has(final Player player, final long amountMinor) {
        return this.economy != null && this.economy.has(player, Money.toMajor(amountMinor, this.currencyScale));
    }

    public EconomyResult withdraw(final Player player, final long amountMinor) {
        return this.wrap(this.economy.withdrawPlayer(player, Money.toMajor(amountMinor, this.currencyScale)));
    }

    public EconomyResult deposit(final OfflinePlayer player, final long amountMinor) {
        return this.wrap(this.economy.depositPlayer(player, Money.toMajor(amountMinor, this.currencyScale)));
    }

    public String format(final long amountMinor) {
        if (this.economy == null) {
            return String.format(Locale.US, "%,." + this.currencyScale + "f", Money.toMajor(amountMinor, this.currencyScale));
        }

        return this.economy.format(Money.toMajor(amountMinor, this.currencyScale));
    }

    public String providerName() {
        if (this.economy == null) {
            return "unknown";
        }

        final String providerName = this.economy.getName();
        if (providerName != null && !providerName.isBlank()) {
            return providerName;
        }

        final String className = this.economy.getClass().getSimpleName();
        return className.isBlank() ? this.economy.getClass().getName() : className;
    }

    private EconomyResult wrap(final EconomyResponse response) {
        if (response == null) {
            return new EconomyResult(false, "No response was returned by the economy provider.");
        }

        final String message = response.errorMessage == null ? "" : response.errorMessage;
        return new EconomyResult(response.transactionSuccess(), message);
    }

    public record EconomyResult(boolean success, String errorMessage) {
    }
}
