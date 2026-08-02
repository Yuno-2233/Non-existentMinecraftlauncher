package com.launcher.core;

public class StopEvent extends LifecycleEvent {
    public StopEvent() {
        super(ModState.STOP);
    }
}
