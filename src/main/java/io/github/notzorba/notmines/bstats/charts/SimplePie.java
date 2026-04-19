/*
 * Vendored from bStats Metrics 3.2.1 under the MIT License.
 */
package io.github.notzorba.notmines.bstats.charts;

import io.github.notzorba.notmines.bstats.json.JsonObjectBuilder;
import java.util.concurrent.Callable;

public final class SimplePie extends CustomChart {
    private final Callable<String> callable;

    public SimplePie(final String chartId, final Callable<String> callable) {
        super(chartId);
        this.callable = callable;
    }

    @Override
    protected JsonObjectBuilder.JsonObject getChartData() throws Exception {
        final String value = this.callable.call();
        if (value == null || value.isEmpty()) {
            return null;
        }

        return new JsonObjectBuilder()
            .appendField("value", value)
            .build();
    }
}
