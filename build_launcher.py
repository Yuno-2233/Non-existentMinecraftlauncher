import os
import subprocess
import zipfile
import shutil

# 1. 基础配置
MODULE_NAME = "github.com/Yuno-2233/Non-existentMinecraftlauncher"
BUILD_DIR = "tmp_wasm_build"
BUILTIN_DIR = "builtin_mods"

# 2. 核心模块源码 (纯 Go 代码，无转义干扰)
CORE_AUTH_GO = """package main

import "unsafe"

//go:wasmimport env host_log
func hostLog(level uint32, msgPtr uintptr, msgLen uint32)

//go:wasmimport env host_subscribe_event
func hostSubscribeEvent(namePtr uintptr, nameLen uint32, callbackID uint32)

//go:wasmimport env host_emit_event
func hostEmitEvent(namePtr uintptr, nameLen uint32, payloadPtr uintptr, payloadLen uint32)

func logMsg(level uint32, msg string) {
    b := []byte(msg)
    hostLog(level, uintptr(unsafe.Pointer(&b[0])), uint32(len(b)))
}

func Info(msg string) { logMsg(1, msg) }

func subscribe(event string, cb uint32) {
    b := []byte(event)
    hostSubscribeEvent(uintptr(unsafe.Pointer(&b[0])), uint32(len(b)), cb)
}

func emit(event string, payload string) {
    ev := []byte(event)
    pl := []byte(payload)
    var plPtr uintptr
    if len(pl) > 0 { plPtr = uintptr(unsafe.Pointer(&pl[0])) }
    hostEmitEvent(uintptr(unsafe.Pointer(&ev[0])), uint32(len(ev)), plPtr, uint32(len(pl)))
}

const cbLauncherReady = 1

//export on_event
func onEvent(callbackID uint32, payloadPtr uintptr, payloadLen uint32) {
    if callbackID != cbLauncherReady { return }
    Info("[CoreAuth] launcher.ready received, performing offline login...")
    emit("core.auth.ready", `{"name":"Player","uuid":"00000000-0000-0000-0000-000000000000","type":"offline"}`)
    Info("[CoreAuth] Offline session created: Player")
}

//export mod_init
func modInit() {
    Info("[CoreAuth] Built-in authentication module initialized")
    subscribe("launcher.ready", cbLauncherReady)
}

func main() {}
"""

CORE_AUTH_JSON = '{"id":"core.auth","name":"Core Authentication","version":"1.0.0","entrypoint":"main.wasm","builtin":true}'

UI_GO = """package main

import "unsafe"

//go:wasmimport env host_log
func hostLog(level uint32, msgPtr uintptr, msgLen uint32)

//go:wasmimport env host_subscribe_event
func hostSubscribeEvent(namePtr uintptr, nameLen uint32, callbackID uint32)

func logMsg(level uint32, msg string) {
    b := []byte(msg)
    hostLog(level, uintptr(unsafe.Pointer(&b[0])), uint32(len(b)))
}

func Info(msg string) { logMsg(1, msg) }

func subscribe(event string, cb uint32) {
    b := []byte(event)
    hostSubscribeEvent(uintptr(unsafe.Pointer(&b[0])), uint32(len(b)), cb)
}

const cbAuthReady = 10

//export on_event
func onEvent(callbackID uint32, payloadPtr uintptr, payloadLen uint32) {
    if callbackID != cbAuthReady { return }
    Info("[UI] core.auth.ready received!")
}

//export mod_init
func modInit() {
    Info("[UI] Default Launcher UI initialized")
    subscribe("core.auth.ready", cbAuthReady)
}

func main() {}
"""

UI_JSON = '{"id":"default-launcher-ui","name":"Default Launcher UI","version":"0.6.0","entrypoint":"main.wasm"}'

# 3. 引擎和主程序源码 (已修复所有换行符问题)
EMBED_GO = """package main

import "embed"

//go:embed builtin_mods/*.zip
var BuiltinModsFS embed.FS
"""

ENGINE_GO = """package runtime

import (
    "archive/zip"
    "bytes"
    "context"
    "fmt"
    "io"
    "io/fs"
    "path/filepath"
    "strings"
    "sync"

    "github.com/tetratelabs/wazero"
    "github.com/tetratelabs/wazero/api"
    "github.com/tetratelabs/wazero/imports/wasi_snapshot_preview1"
)


var builtinModsFS fs.FS

func SetBuiltinFS(fsys fs.FS) { builtinModsFS = fsys }

type Engine struct {
    modsDir       string
    wazeroRuntime wazero.Runtime
    subscriptions map[string][]struct{ mod string; cb uint32 }
    modInstances  map[string]api.Module
    mu            sync.RWMutex
}

func NewEngine(modsDir string) *Engine {
    return &Engine{
        modsDir:       modsDir,
        subscriptions: make(map[string][]struct{ mod string; cb uint32 }),
        modInstances:  make(map[string]api.Module),
    }
}

func (e *Engine) Start() error {
    fmt.Println("[Engine] Starting...")
    e.wazeroRuntime = wazero.NewRuntimeWithConfig(context.Background(), wazero.NewRuntimeConfigCompiler())
    wasi_snapshot_preview1.MustInstantiate(context.Background(), e.wazeroRuntime)
    
    _, err := e.wazeroRuntime.NewHostModuleBuilder("env").
        NewFunctionBuilder().WithFunc(e.hostLog).Export("host_log").
        NewFunctionBuilder().WithFunc(e.hostSubscribeEvent).Export("host_subscribe_event").
        NewFunctionBuilder().WithFunc(e.hostEmitEventFromMod).Export("host_emit_event").
        Instantiate(context.Background())
    if err != nil { return err }
    fmt.Println("[Engine] [OK] Host APIs registered")

    if err := e.loadBuiltinMods(); err != nil { return err }
    fmt.Printf("[Engine] Total: %d mod(s) loaded\\n", len(e.modInstances))
    
    go func() { e.hostEmitEvent(context.Background(), "launcher.ready", "") }()
    return nil
}

func (e *Engine) hostLog(ctx context.Context, mod api.Module, level, msgPtr, msgLen uint32) {
    b, _ := mod.Memory().Read(msgPtr, msgLen)
    fmt.Printf("[Mod] [INFO] %s\\n", string(b))
}

func (e *Engine) hostSubscribeEvent(ctx context.Context, mod api.Module, np, nl, cid uint32) {
    b, _ := mod.Memory().Read(np, nl)
    name := string(b)
    e.mu.Lock()
    e.subscriptions[name] = append(e.subscriptions[name], struct{ mod string; cb uint32 }{mod.Name(), cid})
    e.mu.Unlock()
    fmt.Printf("[Engine] [EVENT] '%s' subscribed to '%s'\\n", mod.Name(), name)
}

func (e *Engine) hostEmitEvent(ctx context.Context, name, payload string) {
    fmt.Printf("[Engine] [EVENT] Emit '%s'\\n", name)
    e.mu.RLock()
    subs := e.subscriptions[name]
    e.mu.RUnlock()
    
    pb := []byte(payload)
    for _, s := range subs {
        inst := e.modInstances[s.mod]
        if inst == nil { continue }
        fn := inst.ExportedFunction("on_event")
        if fn == nil { continue }
        
        var pp uint32
        if len(pb) > 0 {
            af := inst.ExportedFunction("malloc")
            if af != nil {
                r, _ := af.Call(ctx, uint64(len(pb)))
                pp = uint32(r[0])
                inst.Memory().Write(pp, pb)
            }
        }
        fn.Call(ctx, uint64(s.cb), uint64(pp), uint64(len(pb)))
    }
}

func (e *Engine) hostEmitEventFromMod(ctx context.Context, mod api.Module, np, nl, pp, pl uint32) {
    nb, _ := mod.Memory().Read(np, nl)
    pb, _ := mod.Memory().Read(pp, pl)
    e.hostEmitEvent(ctx, string(nb), string(pb))
}

func (e *Engine) loadBuiltinMods() error {
    if builtinModsFS == nil { return nil }
    entries, _ := fs.ReadDir(builtinModsFS, "builtin_mods")
    for _, ent := range entries {
        if ent.IsDir() || !strings.HasSuffix(ent.Name(), ".zip") { continue }
        data, _ := fs.ReadFile(builtinModsFS, filepath.Join("builtin_mods", ent.Name()))
        e.loadModFromBytes(data)
    }
    return nil
}

func (e *Engine) loadModFromBytes(zd []byte) error {
    r, _ := zip.NewReader(bytes.NewReader(zd), int64(len(zd)))
    var id string
    var wb []byte
    for _, f := range r.File {
        rc, _ := f.Open()
        d, _ := io.ReadAll(rc)
        rc.Close()
        if f.Name == "mod.json" {
            s := string(d)
            idx := strings.Index(s, `"id":"`)
            if idx != -1 {
                s = s[idx+6:]
                id = s[:strings.Index(s, `"`)]
            }
        }
        if filepath.Ext(f.Name) == ".wasm" { wb = d }
    }
    if id == "" || len(wb) == 0 { return nil }

    compiled, _ := e.wazeroRuntime.CompileModule(context.Background(), wb)
    cfg := wazero.NewModuleConfig().WithName(id).WithStartFunctions()
    inst, err := e.wazeroRuntime.InstantiateModule(context.Background(), compiled, cfg)
    if err != nil { return err }

    if fn := inst.ExportedFunction("mod_init"); fn != nil {
        fn.Call(context.Background())
    }
    e.modInstances[id] = inst
    fmt.Printf("[Loader] [OK] %s\\n", id)
    return nil
}

func (e *Engine) Shutdown() {
    fmt.Println("[Engine] Shutting down...")
    if e.wazeroRuntime != nil { e.wazeroRuntime.Close(context.Background()) }
}
"""

MAIN_GO = """package main

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
        fmt.Fprintf(os.Stderr, "[FATAL] %v\\n", err)
        os.Exit(1)
    }
    fmt.Println("[Main] Waiting for interrupt signal...")
    c := make(chan os.Signal, 1)
    signal.Notify(c, syscall.SIGINT, syscall.SIGTERM)
    <-c
    engine.Shutdown()
}
"""

# 4. 构建逻辑
def run_cmd(cmd, env=None, cwd=None):
    print(f"   > {' '.join(cmd)}")
    subprocess.run(cmd, check=True, env=env, cwd=cwd)

def build_wasm(name, go_code, json_code):
    print(f"   Compiling Wasm: {name}")
    os.makedirs(BUILD_DIR, exist_ok=True)
    with open(os.path.join(BUILD_DIR, "main.go"), "w") as f: f.write(go_code)
    
    env = os.environ.copy()
    env["GOOS"] = "wasip1"
    env["GOARCH"] = "wasm"
    run_cmd(["go", "build", "-o", "main.wasm", "."], env=env, cwd=BUILD_DIR)
    
    zip_path = os.path.join(BUILD_DIR, f"{name}.zip")
    with zipfile.ZipFile(zip_path, 'w') as zf:
        zf.write(os.path.join(BUILD_DIR, "main.wasm"), "main.wasm")
        zf.writestr("mod.json", json_code)
    return zip_path

def main():
    print("[1/6] Preparing directories...")
    os.makedirs("cmd/launcher", exist_ok=True)
    os.makedirs("runtime", exist_ok=True)
    os.makedirs(BUILTIN_DIR, exist_ok=True)
    os.makedirs("mods", exist_ok=True)
    
    print("[2/6] Writing Go source files...")
    with open("cmd/launcher/embed.go", "w") as f: f.write(EMBED_GO)
    with open("runtime/engine.go", "w") as f: f.write(ENGINE_GO)
    with open("cmd/launcher/main.go", "w") as f: f.write(MAIN_GO)
    
    print("[3/6] Building Wasm Modules...")
    core_zip = build_wasm("core-auth", CORE_AUTH_GO, CORE_AUTH_JSON)
    ui_zip = build_wasm("ui", UI_GO, UI_JSON)
    
    print("[4/6] Packaging builtin mods...")
    launcher_builtin_dir = os.path.join("cmd", "launcher", BUILTIN_DIR)
    os.makedirs(launcher_builtin_dir, exist_ok=True)
    shutil.copy(core_zip, os.path.join(launcher_builtin_dir, "core-auth.zip"))
    shutil.copy(ui_zip, os.path.join(launcher_builtin_dir, "ui.zip"))
    shutil.rmtree(BUILD_DIR)
    
    print("[5/6] Initializing Go Module...")
    if not os.path.exists("go.mod"):
        run_cmd(["go", "mod", "init", MODULE_NAME])
    run_cmd(["go", "mod", "tidy"])
    
    print("[6/6] Running Launcher...")
    run_cmd(["go", "run", "./cmd/launcher"])

if __name__ == "__main__":
    main()
