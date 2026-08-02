package com.launcher.core;

public abstract class LifecycleEvent extends Event {
    private final ModState state;

    public LifecycleEvent(ModState state) {
        this.state = state;
    }

    public ModState getState() {
        return state;
    }
}
