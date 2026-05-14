package me.leoko.advancedban.utils.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * A parsed Discord embed template loaded from {@code discord/embed/<name>.json}.
 *
 * The DBA file format wraps the embed under the "embed" key:
 * <pre>{ "embed": { "title": "...", "description": "...", "color": 12345, "footer": { ... } } }</pre>
 *
 * This class stores the raw JSON tree and produces a placeholder-resolved copy on each
 * {@link #apply(Map)} call. The original tree is never mutated.
 */
public final class EmbedTemplate {

    private final JsonElement template;

    public EmbedTemplate(String rawJson) {
        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
        this.template = root.has("embed") ? root.get("embed") : root;
    }

    /**
     * Apply the placeholder map to the template and return a fresh JsonElement
     * that can be embedded inside a Discord webhook payload's "embeds" array.
     */
    public JsonElement apply(Map<String, String> placeholders) {
        return resolve(template, placeholders);
    }

    private static JsonElement resolve(JsonElement element, Map<String, String> placeholders) {
        if (element.isJsonObject()) {
            JsonObject src = element.getAsJsonObject();
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : src.entrySet()) {
                out.add(entry.getKey(), resolve(entry.getValue(), placeholders));
            }
            return out;
        }
        if (element.isJsonArray()) {
            com.google.gson.JsonArray src = element.getAsJsonArray();
            com.google.gson.JsonArray out = new com.google.gson.JsonArray();
            for (JsonElement item : src) {
                out.add(resolve(item, placeholders));
            }
            return out;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new com.google.gson.JsonPrimitive(replacePlaceholders(element.getAsString(), placeholders));
        }
        return element;
    }

    static String replacePlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || input.indexOf('%') == -1) return input;
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = "%" + entry.getKey() + "%";
            if (result.contains(key)) {
                result = result.replace(key, entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return result;
    }
}
