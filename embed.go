package main

import "embed"

//go:embed builtin_mods/*.zip
var BuiltinModsFS embed.FS
