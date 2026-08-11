package com.github.yuno2233.neml.mod;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class SimpleJsonParser {
    private static final Gson gson = new Gson();

    public static ModMetadata parseModMetadata(String json) throws Exception {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> map = gson.fromJson(json, type);

        ModMetadata meta = new ModMetadata();
        meta.setId((String) map.get("id"));
        meta.setVersion((String) map.get("version"));
        meta.setMainClass((String) map.get("mainClass"));

        // depends
        Map<String, String> depends = null;
        Object dependsObj = map.get("depends");
        if (dependsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> depMap = (Map<String, Object>) dependsObj;
            depends = new java.util.HashMap<>();
            for (Map.Entry<String, Object> e : depMap.entrySet()) {
                depends.put(e.getKey(), e.getValue().toString());
            }
        }
        meta.setDepends(depends);

        // entrypoints
        Map<String, List<String>> entrypoints = null;
        Object entryObj = map.get("entrypoints");
        if (entryObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> epMap = (Map<String, Object>) entryObj;
            entrypoints = new java.util.HashMap<>();
            for (Map.Entry<String, Object> e : epMap.entrySet()) {
                Object val = e.getValue();
                if (val instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> list = (List<String>) val;
                    entrypoints.put(e.getKey(), list);
                }
            }
        }
        meta.setEntrypoints(entrypoints);
        
        // 解析 commands 字段 (命令名 -> 描述)
        Object commandsObj = map.get("commands");
        if (commandsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cmdMap = (Map<String, Object>) commandsObj;
            Map<String, String> commands = new java.util.HashMap<>();
            for (Map.Entry<String, Object> e : cmdMap.entrySet()) {
                commands.put(e.getKey(), e.getValue().toString());
            }
            meta.setCommands(commands);
        }

        return meta;
    }
}