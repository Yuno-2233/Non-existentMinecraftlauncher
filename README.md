

## 标题
`Non-existentMinecraftLauncher (NEML) Engine`
副标题或简介：一个极简、无图形界面、基于 Mod 化架构的 Minecraft 启动器引擎。

## 项目简介
NEML 引擎本身不包含任何 Minecraft 启动功能，所有实际能力（如版本安装、游戏启动、账号认证等）均由 Mod 提供。引擎仅负责 Mod 的发现、依赖解析、加载排序和命令分发，同时提供统一的日志接口与基础 API。

引擎采用单次命令运行模式：
```
java -jar neml.jar <modid> [参数...]
```
每次执行仅加载目标 Mod 及其依赖树，其他 Mod 不参与，从而实现冷启动零感知。适用于需要深度定制启动流程的开发者，或作为其他启动器的底层驱动核心。

## 主要特性
- 纯 CLI 交互，无任何图形界面
- 支持内置 Mod 与外部 Mod 双重加载机制
- 完整的版本依赖声明与拓扑排序加载
- 每个外部 Mod 拥有独立 ClassLoader，类隔离安全
- 内置 Mod 以源码形式存放在标准目录下，编译时自动集成
- 轻量日志系统，同时输出到控制台和文件，自动按日期滚动，并可防止日志文件堆积
- 严格遵守 Maven 标准项目结构，跨平台 JVM 运行

## 快速开始
1. 环境要求：Java 11 或更高版本，Maven（仅从源码构建时需要）
2. 获取引擎 Jar：
- 从 GitHub Releases 下载已构建的 `neml.jar`
- 或自行构建：`mvn clean package`，产物位于 `target/neml.jar`
3. 运行：
```
java -jar neml.jar core list
```
查看已发现的 Mod 列表
4. 探索更多命令：
```
java -jar neml.jar core help
```

## 内置 Mod
引擎预置一个名为 `core` 的内置 Mod，提供基础管理命令：

| 命令 | 说明 |
|------|------|
| `core list` | 列出所有已发现的 Mod（内置与外部） |
| `core reload` | 重新扫描 Mod 目录 |
| `core help` | 显示 core mod 的帮助信息 |

示例：
```
java -jar neml.jar core list
java -jar neml.jar core reload
java -jar neml.jar core help
```

## Mod 开发指南
完整的 Mod 开发规范请查阅 `docs/BUILTIN_MOD_SPEC.md`。关键点概括：
- 内置 Mod 源码置于 `src/main/builtin-mods/<modid>/` 目录
- 每个 Mod 目录根必须包含 `neml-mod.json` 描述元数据
- 入口点分为 `main`（初始化）和 `command`（命令执行），需实现对应的 API 接口
- 依赖通过 `depends` 字段声明，引擎自动进行版本校验与加载排序
- 编译时通过 Maven 插件自动将源码和资源集成到最终 Jar 包中

## 项目结构
```
├── pom.xml
├── src
│   └── main
│       ├── java/com/github/yuno2233/neml   （引擎核心代码）
│       ├── resources/META-INF/neml/builtin  （构建后自动生成的内置 Mod 资源）
│       └── builtin-mods                     （内置 Mod 源码目录）
│           └── core
│               ├── neml-mod.json
│               └── io/github/...
└── docs
    └── BUILTIN_MOD_SPEC.md                  （Mod 开发规范文档）
```

## 未来计划
- 实现 Minecraft 原版安装 Mod（`installer`）
- 实现游戏启动 Mod（`launcher`），支持离线登录与参数配置
- 支持外部 Mod 的完整生命周期管理
- 提供更灵活的命令注册与帮助生成机制

## 法律信息

### 开源协议
本项目采用 GNU General Public License v3.0 (GPL v3) 进行许可。
详见[GPL v3官方文档](https://www.gnu.org/licenses/gpl-3.0.txt)

