cat << 'EOF' > build_mod.go
package main

import (
	"archive/zip"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
)

func main() {
	modDir := "test-mod"
	wasmFile := filepath.Join(modDir, "main.wasm")
	zipOut := "mods/default-launcher-ui.zip"

	// 1. 确保 test-mod 目录和文件存在
	if err := os.MkdirAll(modDir, 0755); err != nil {
		panic(err)
	}

	// 写入 go.mod
	os.WriteFile(filepath.Join(modDir, "go.mod"), []byte("module test-mod\n"), 0644)

	// 写入 main.go
	mainGo := `package main
import "fmt"
func main() {
	fmt.Println("👋 Hello from Wasm sandbox!")
	fmt.Println("🎮 Non-existentMinecraftlauncher Mod is running!")
}
`
	os.WriteFile(filepath.Join(modDir, "main.go"), []byte(mainGo), 0644)

	// 2. 在 test-mod 目录下执行 tinygo build
	fmt.Println("🔨 Compiling Wasm with TinyGo...")
	cmd := exec.Command("tinygo", "build", "-o", "main.wasm", "-target=wasi", ".")
	cmd.Dir = modDir // ✅ 关键：强制设置工作目录
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		fmt.Println("❌ TinyGo build failed:", err)
		os.Exit(1)
	}

	// 3. 校验 wasm 文件
	info, err := os.Stat(wasmFile)
	if err != nil || info.Size() == 0 {
		fmt.Println("❌ Error: main.wasm is missing or empty!")
		os.Exit(1)
	}
	fmt.Printf("✅ Wasm compiled successfully (%d bytes)\n", info.Size())

	// 4. 打包成 ZIP
	os.MkdirAll("mods", 0755)
	f, _ := os.Create(zipOut)
	defer f.Close()
	w := zip.NewWriter(f)
	defer w.Close()

	mj, _ := w.Create("mod.json")
	mj.Write([]byte(`{"id":"default-launcher-ui","name":"Default Launcher UI","version":"0.1.0","entrypoint":"main.wasm"}`))

	wb, _ := os.ReadFile(wasmFile)
	wf, _ := w.Create("main.wasm")
	wf.Write(wb)

	fmt.Println("✅ Mod ZIP created:", zipOut)
}
EOF

# 运行构建器（不依赖任何 shell 脚本）
go run build_mod.go && rm build_mod.go
