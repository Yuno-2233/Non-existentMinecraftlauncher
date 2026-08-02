package com.launcher.core;

public class ConstructEvent extends LifecycleEvent {
    public ConstructEvent() {
        super(ModState.CONSTRUCT);
    }
}
