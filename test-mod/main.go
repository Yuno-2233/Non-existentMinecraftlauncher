package main

import "unsafe"

//go:wasmimport env host_log
func hostLog(level uint32, msgPtr uintptr, msgLen uint32)

//go:wasmimport env host_get_config
func hostGetConfig(keyPtr uintptr, keyLen uint32, valBufPtr uintptr, valBufCap uint32) uint32

//go:wasmimport env host_subscribe_event
func hostSubscribeEvent(namePtr uintptr, nameLen uint32, callbackID uint32)

//go:wasmimport env host_emit_event
func hostEmitEvent(namePtr uintptr, nameLen uint32, payloadPtr uintptr, payloadLen uint32)

func logMsg(level uint32, msg string) {
b := []byte(msg)
ptr := uintptr(unsafe.Pointer(&b[0]))
hostLog(level, ptr, uint32(len(b)))
}

func Info(msg string)  { logMsg(1, msg) }
func Warn(msg string)  { logMsg(2, msg) }

func getConfig(key string) (string, bool) {
keyBytes := []byte(key)
keyPtr := uintptr(unsafe.Pointer(&keyBytes[0]))
needed := hostGetConfig(keyPtr, uint32(len(keyBytes)), 0, 0)
if needed == 0xFFFFFFFF {
return "", false
}
if needed == 0 {
return "", true
}
buf := make([]byte, needed)
bufPtr := uintptr(unsafe.Pointer(&buf[0]))
hostGetConfig(keyPtr, uint32(len(keyBytes)), bufPtr, needed)
return string(buf), true
}

func subscribe(event string, callbackID uint32) {
b := []byte(event)
ptr := uintptr(unsafe.Pointer(&b[0]))
hostSubscribeEvent(ptr, uint32(len(b)), callbackID)
}

func emit(event string, payload string) {
evBytes := []byte(event)
evPtr := uintptr(unsafe.Pointer(&evBytes[0]))
plBytes := []byte(payload)
var plPtr uintptr
if len(plBytes) > 0 {
plPtr = uintptr(unsafe.Pointer(&plBytes[0]))
}
hostEmitEvent(evPtr, uint32(len(evBytes)), plPtr, uint32(len(plBytes)))
}

const (
cbLauncherReady = 1
cbUiClick       = 2
)

//export on_event
func onEvent(callbackID uint32, payloadPtr uintptr, payloadLen uint32) {
var payload string
if payloadLen > 0 {
buf := make([]byte, payloadLen)
for i := uint32(0); i < payloadLen; i++ {
buf[i] = *(*byte)(unsafe.Pointer(payloadPtr + uintptr(i)))
}
payload = string(buf)
}
switch callbackID {
case cbLauncherReady:
Info("[EventHandler] launcher.ready received")
emit("ui.click", "button:start_game")
case cbUiClick:
Info("[EventHandler] ui.click received: " + payload)
default:
Warn("[EventHandler] unknown callbackID")
}
}

//export mod_init
func modInit() {
Info("Hello from Wasm sandbox!")
subscribe("launcher.ready", cbLauncherReady)
subscribe("ui.click", cbUiClick)
Info("Subscribed to launcher.ready and ui.click")
Info("Default Launcher UI ready!")
// No blocking needed. Module stays idle and callable via exported functions.
}

func main() {}
