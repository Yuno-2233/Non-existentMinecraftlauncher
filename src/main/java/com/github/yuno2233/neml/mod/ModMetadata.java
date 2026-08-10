package com.github.yuno2233.neml.mod;

import java.util.*;

public class ModMetadata {
    private String id;
    private String version;
    private String mainClass;
    private Map<String, String> depends = new HashMap<>();
    private Map<String, List<String>> entrypoints = new HashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getMainClass() { return mainClass; }
    public void setMainClass(String mainClass) { this.mainClass = mainClass; }
    public Map<String, String> getDepends() { return depends; }
    public void setDepends(Map<String, String> depends) { this.depends = depends; }
    public Map<String, List<String>> getEntrypoints() { return entrypoints; }
    public void setEntrypoints(Map<String, List<String>> entrypoints) { this.entrypoints = entrypoints; }
}
