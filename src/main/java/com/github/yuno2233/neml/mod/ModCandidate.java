package com.github.yuno2233.neml.mod;

import java.nio.file.Path;

public class ModCandidate {
    private final Path jarPath;
    private final boolean builtin;
    private ModMetadata metadata;

    public ModCandidate(Path jarPath, boolean builtin) {
        this.jarPath = jarPath;
        this.builtin = builtin;
    }
    public Path getJarPath() { return jarPath; }
    public boolean isBuiltin() { return builtin; }
    public ModMetadata getMetadata() { return metadata; }
    public void setMetadata(ModMetadata metadata) { this.metadata = metadata; }
    public String getId() { return metadata != null ? metadata.getId() : jarPath.getFileName().toString(); }
    
    public String getSource() {
        return builtin ? "内置" : "外部";
    }
}
