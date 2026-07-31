package main

import (
    "fmt"
    "os"
    "os/signal"
    "syscall"
    "github.com/Yuno-2233/Non-existentMinecraftlauncher/runtime"
)

func main() {
    runtime.SetBuiltinFS(BuiltinModsFS)
    engine := runtime.NewEngine("./mods")
    if err := engine.Start(); err != nil {
        fmt.Fprintf(os.Stderr, "[FATAL] %v\n", err)
        os.Exit(1)
    }
    fmt.Println("[Main] Waiting for interrupt signal...")
    c := make(chan os.Signal, 1)
    signal.Notify(c, syscall.SIGINT, syscall.SIGTERM)
    <-c
    engine.Shutdown()
}
