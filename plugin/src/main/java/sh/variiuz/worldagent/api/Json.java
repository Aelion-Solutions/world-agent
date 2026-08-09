package sh.variiuz.worldagent.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class Json {

    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private Json() {
    }

    public static JsonObject obj() {
        return new JsonObject();
    }

    public static JsonObject parseObject(String body) {
        if (body == null || body.isBlank()) {
            return new JsonObject();
        }
        JsonElement el = JsonParser.parseString(body);
        return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
    }

    public static String stringify(Object value) {
        return GSON.toJson(value);
    }
}
