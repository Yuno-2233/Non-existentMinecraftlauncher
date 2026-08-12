package com.github.yuno2233.neml.mod;

import com.google.gson.Gson;
import java.util.*;

public class SimpleJsonParser {
    private static final Gson gson = new Gson();

    public static ModMetadata parseModMetadata(String json) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = gson.fromJson(json, Map.class);
        ModMetadata meta = new ModMetadata();

        if (map.containsKey("id")) meta.setId((String) map.get("id"));
        if (map.containsKey("version")) meta.setVersion((String) map.get("version"));
        if (map.containsKey("mainClass")) meta.setMainClass((String) map.get("mainClass"));
        if (map.containsKey("description")) meta.setDescription((String) map.get("description"));

        // depends
        if (map.containsKey("depends")) {
            Object dependsObj = map.get("depends");
            if (dependsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> depMap = (Map<String, Object>) dependsObj;
                Map<String, String> depends = new HashMap<>();
                for (Map.Entry<String, Object> e : depMap.entrySet()) {
                    depends.put(e.getKey(), e.getValue().toString());
                }
                meta.setDepends(depends);
            }
        }

        // entrypoints
        if (map.containsKey("entrypoints")) {
            Object entryObj = map.get("entrypoints");
            if (entryObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> epMap = (Map<String, Object>) entryObj;
                Map<String, List<String>> entrypoints = new HashMap<>();
                for (Map.Entry<String, Object> e : epMap.entrySet()) {
                    Object val = e.getValue();
                    if (val instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> list = (List<String>) val;
                        entrypoints.put(e.getKey(), list);
                    }
                }
                meta.setEntrypoints(entrypoints);
            }
        }

        // commands 兼容字符串和对象（值可能是 String 或 Map）
        if (map.containsKey("commands")) {
            Object commandsObj = map.get("commands");
            if (commandsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cmdMap = (Map<String, Object>) commandsObj;
                Map<String, Object> commands = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : cmdMap.entrySet()) {
                    String key = entry.getKey();
                    Object val = entry.getValue();
                    if (val instanceof String) {
                        commands.put(key, val);
                    } else if (val instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> innerMap = (Map<String, Object>) val;
                        commands.put(key, innerMap);
                    }
                }
                meta.setCommands(commands);
            }
        }

        return meta;
    }
}