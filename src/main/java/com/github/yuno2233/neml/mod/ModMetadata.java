package com.github.yuno2233.neml.mod;

import java.util.*;

public class ModMetadata {
    private String id;
    private String version;
    private String mainClass;
    private String description;
    private Map<String, String> depends;            // 保持 String
    private Map<String, Object> commands;           // 兼容对象
    private Map<String, List<String>> entrypoints;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getMainClass() { return mainClass; }
    public void setMainClass(String mainClass) { this.mainClass = mainClass; }

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, String> getDepends() {
        return depends != null ? depends : Collections.emptyMap();
    }
    public void setDepends(Map<String, String> depends) { this.depends = depends; }

    public Map<String, List<String>> getEntrypoints() {
        return entrypoints != null ? entrypoints : Collections.emptyMap();
    }
    public void setEntrypoints(Map<String, List<String>> entrypoints) { this.entrypoints = entrypoints; }

    public void setCommands(Map<String, Object> commands) { this.commands = commands; }

    public Map<String, Object> getCommandsRaw() {
        return commands != null ? commands : Collections.emptyMap();
    }

    // 旧接口返回简化描述
    public Map<String, String> getCommands() {
        if (commands == null) return Collections.emptyMap();
        Map<String, String> simple = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : commands.entrySet()) {
            if (entry.getValue() instanceof String) {
                simple.put(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> infoMap = (Map<String, String>) entry.getValue();
                simple.put(entry.getKey(), infoMap.getOrDefault("description", ""));
            }
        }
        return simple;
    }
}