package runtime

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
    fmt.Printf("[Engine] Total: %d mod(s) loaded\n", len(e.modInstances))
    
    go func() { e.hostEmitEvent(context.Background(), "launcher.ready", "") }()
    return nil
}

func (e *Engine) hostLog(ctx context.Context, mod api.Module, level, msgPtr, msgLen uint32) {
    b, _ := mod.Memory().Read(msgPtr, msgLen)
    fmt.Printf("[Mod] [INFO] %s\n", string(b))
}

func (e *Engine) hostSubscribeEvent(ctx context.Context, mod api.Module, np, nl, cid uint32) {
    b, _ := mod.Memory().Read(np, nl)
    name := string(b)
    e.mu.Lock()
    e.subscriptions[name] = append(e.subscriptions[name], struct{ mod string; cb uint32 }{mod.Name(), cid})
    e.mu.Unlock()
    fmt.Printf("[Engine] [EVENT] '%s' subscribed to '%s'\n", mod.Name(), name)
}

func (e *Engine) hostEmitEvent(ctx context.Context, name, payload string) {
    fmt.Printf("[Engine] [EVENT] Emit '%s'\n", name)
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
    fmt.Printf("[Loader] [OK] %s\n", id)
    return nil
}

func (e *Engine) Shutdown() {
    fmt.Println("[Engine] Shutting down...")
    if e.wazeroRuntime != nil { e.wazeroRuntime.Close(context.Background()) }
}
