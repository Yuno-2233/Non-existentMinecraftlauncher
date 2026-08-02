package com.launcher.core;

public class InitializeEvent extends LifecycleEvent {
    public InitializeEvent() {
        super(ModState.INITIALIZE);
    }
}
