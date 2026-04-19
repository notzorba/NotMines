/*
 * Vendored from bStats Metrics 3.2.1 under the MIT License.
 */
package io.github.notzorba.notmines.bstats.json;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class JsonObjectBuilder {
    private StringBuilder builder = new StringBuilder();
    private boolean hasAtLeastOneField = false;

    public JsonObjectBuilder() {
        this.builder.append("{");
    }

    public JsonObjectBuilder appendNull(final String key) {
        this.appendFieldUnescaped(key, "null");
        return this;
    }

    public JsonObjectBuilder appendField(final String key, final String value) {
        if (value == null) {
            throw new IllegalArgumentException("JSON value must not be null");
        }
        this.appendFieldUnescaped(key, "\"" + escape(value) + "\"");
        return this;
    }

    public JsonObjectBuilder appendField(final String key, final int value) {
        this.appendFieldUnescaped(key, String.valueOf(value));
        return this;
    }

    public JsonObjectBuilder appendField(final String key, final JsonObject object) {
        if (object == null) {
            throw new IllegalArgumentException("JSON object must not be null");
        }
        this.appendFieldUnescaped(key, object.toString());
        return this;
    }

    public JsonObjectBuilder appendField(final String key, final String[] values) {
        if (values == null) {
            throw new IllegalArgumentException("JSON values must not be null");
        }

        final String escapedValues = Arrays.stream(values)
            .map(value -> "\"" + escape(value) + "\"")
            .collect(Collectors.joining(","));
        this.appendFieldUnescaped(key, "[" + escapedValues + "]");
        return this;
    }

    public JsonObjectBuilder appendField(final String key, final int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("JSON values must not be null");
        }

        final String escapedValues = Arrays.stream(values)
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(","));
        this.appendFieldUnescaped(key, "[" + escapedValues + "]");
        return this;
    }

    public JsonObjectBuilder appendField(final String key, final JsonObject[] values) {
        if (values == null) {
            throw new IllegalArgumentException("JSON values must not be null");
        }

        final String escapedValues = Arrays.stream(values)
            .map(JsonObject::toString)
            .collect(Collectors.joining(","));
        this.appendFieldUnescaped(key, "[" + escapedValues + "]");
        return this;
    }

    public JsonObject build() {
        if (this.builder == null) {
            throw new IllegalStateException("JSON has already been built");
        }

        final JsonObject object = new JsonObject(this.builder.append("}").toString());
        this.builder = null;
        return object;
    }

    private void appendFieldUnescaped(final String key, final String escapedValue) {
        if (this.builder == null) {
            throw new IllegalStateException("JSON has already been built");
        }
        if (key == null) {
            throw new IllegalArgumentException("JSON key must not be null");
        }
        if (this.hasAtLeastOneField) {
            this.builder.append(",");
        }
        this.builder.append("\"").append(escape(key)).append("\":").append(escapedValue);
        this.hasAtLeastOneField = true;
    }

    private static String escape(final String value) {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current == '"') {
                builder.append("\\\"");
            } else if (current == '\\') {
                builder.append("\\\\");
            } else if (current <= '\u000F') {
                builder.append("\\u000").append(Integer.toHexString(current));
            } else if (current <= '\u001F') {
                builder.append("\\u00").append(Integer.toHexString(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    public static final class JsonObject {
        private final String value;

        private JsonObject(final String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }
    }
}
