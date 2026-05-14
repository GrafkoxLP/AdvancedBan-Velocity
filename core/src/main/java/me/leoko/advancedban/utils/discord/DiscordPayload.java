package me.leoko.advancedban.utils.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Builder for the JSON body of a Discord webhook POST request.
 * See https://discord.com/developers/docs/resources/webhook#execute-webhook
 */
public final class DiscordPayload {

    private final JsonObject root = new JsonObject();
    private JsonArray embeds;

    public DiscordPayload username(String username) {
        if (username != null && !username.isEmpty()) root.addProperty("username", username);
        return this;
    }

    public DiscordPayload avatarUrl(String url) {
        if (url != null && !url.isEmpty()) root.addProperty("avatar_url", url);
        return this;
    }

    public DiscordPayload content(String content) {
        if (content != null && !content.isEmpty()) root.addProperty("content", content);
        return this;
    }

    public DiscordPayload addEmbed(JsonElement embed) {
        if (embeds == null) {
            embeds = new JsonArray();
            root.add("embeds", embeds);
        }
        embeds.add(embed);
        return this;
    }

    public String toJson() {
        return root.toString();
    }
}
