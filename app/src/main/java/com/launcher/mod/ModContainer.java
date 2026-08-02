package com.launcher.mod;

public class ModContainer {
    private final String modId;
    private final Object instance; // Mod 的实际对象实例

    public ModContainer(String modId, Object instance) {
        this.modId = modId;
        this.instance = instance;
    }

    public String getModId() {
        return modId;
    }

    public Object getInstance() {
        return instance;
    }
}
