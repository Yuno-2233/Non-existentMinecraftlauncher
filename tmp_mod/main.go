package main
import "unsafe"
//go:wasmimport env host_log
func hostLog(level uint32, msgPtr uintptr, msgLen uint32)
//go:wasmimport env host_subscribe_event
func hostSubscribeEvent(namePtr uintptr, nameLen uint32, callbackID uint32)
//go:wasmimport env host_emit_event
func hostEmitEvent(namePtr uintptr, nameLen uint32, payloadPtr uintptr, payloadLen uint32)
func logMsg(level uint32, msg string) { b := []byte(msg); hostLog(level, uintptr(unsafe.Pointer(&b[0])), uint32(len(b))) }
func Info(msg string) { logMsg(1, msg) }
func subscribe(event string, cb uint32) { b := []byte(event); hostSubscribeEvent(uintptr(unsafe.Pointer(&b[0])), uint32(len(b)), cb) }
func emit(event string, payload string) {
ev := []byte(event); pl := []byte(payload); var plPtr uintptr
if len(pl) > 0 { plPtr = uintptr(unsafe.Pointer(&pl[0])) }
hostEmitEvent(uintptr(unsafe.Pointer(&ev[0])), uint32(len(ev)), plPtr, uint32(len(pl)))
}
const cbLauncherReady = 1
//export on_event
func onEvent(callbackID uint32, payloadPtr uintptr, payloadLen uint32) {
if callbackID != cbLauncherReady { return }
Info("[CoreAuth] launcher.ready received, performing offline login...")
emit("core.auth.ready", "{\"name\":\"Player\",\"uuid\":\"00000000-0000-0000-0000-000000000000\",\"type\":\"offline\"}")
Info("[CoreAuth] Offline session created: Player")
}
//export mod_init
func modInit() { Info("[CoreAuth] Built-in authentication module initialized"); subscribe("launcher.ready", cbLauncherReady) }
func main() {}
