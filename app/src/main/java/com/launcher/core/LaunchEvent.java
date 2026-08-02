package com.launcher.core;

public class LaunchEvent extends LifecycleEvent {
    public LaunchEvent() {
        super(ModState.LAUNCH);
    }
}
