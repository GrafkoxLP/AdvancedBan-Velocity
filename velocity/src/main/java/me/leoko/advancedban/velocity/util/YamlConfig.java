package me.leoko.advancedban.velocity.util;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny YAML wrapper that mirrors the BungeeCord {@code Configuration} API surface
 * AdvancedBan's core uses ({@link #getString}, {@link #getBoolean}, {@link #getInt},
 * {@link #getLong}, {@link #getStringList}, {@link #get}, {@link #getKeys}, …).
 *
 * The data model is a nested {@link Map} rooted at the YAML document. Path traversal
 * uses {@code "."}-separated segments — same convention as the rest of AdvancedBan.
 */
public final class YamlConfig {

    private final Map<String, Object> root;

    private YamlConfig(Map<String, Object> root) {
        this.root = root == null ? new LinkedHashMap<>() : root;
    }

    public static YamlConfig load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return load(in);
        }
    }

    public static YamlConfig load(InputStream in) {
        // SnakeYAML's Yaml is not thread-safe; fresh instance per load is cheap.
        Object loaded = new Yaml().load(in);
        if (loaded == null) {
            return new YamlConfig(new LinkedHashMap<>());
        }
        if (!(loaded instanceof Map)) {
            throw new IllegalArgumentException("YAML root is not a map: " + loaded.getClass());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) loaded;
        return new YamlConfig(map);
    }

    public Object get(String path) {
        if (path == null || path.isEmpty()) return root;
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) current;
            if (!m.containsKey(part)) return null;
            current = m.get(part);
        }
        return current;
    }

    public boolean contains(String path) {
        return get(path) != null;
    }

    public String getString(String path) {
        return getString(path, null);
    }

    public String getString(String path, String def) {
        Object v = get(path);
        return v == null ? def : v.toString();
    }

    public Boolean getBoolean(String path) {
        return getBoolean(path, false);
    }

    public boolean getBoolean(String path, boolean def) {
        Object v = get(path);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) return Boolean.parseBoolean((String) v);
        return def;
    }

    public Integer getInt(String path) {
        return getInt(path, 0);
    }

    public int getInt(String path, int def) {
        Object v = get(path);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public Long getLong(String path) {
        return getLong(path, 0L);
    }

    public long getLong(String path, long def) {
        Object v = get(path);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try {
                return Long.parseLong((String) v);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public List<String> getStringList(String path) {
        Object v = get(path);
        if (!(v instanceof List)) return Collections.emptyList();
        List<?> list = (List<?>) v;
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            out.add(item == null ? null : item.toString());
        }
        return out;
    }

    public String[] getKeys(String path) {
        Object v = path == null || path.isEmpty() ? root : get(path);
        if (!(v instanceof Map)) return new String[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return m.keySet().toArray(new String[0]);
    }
}
