# 配置与数据目录
所有配置文件存放在 `neml/` 目录下。
文件结构: 
```
./neml
├── config  <---------------配置文件夹, 用于存放各mod的配置
│ ├── account
│ │ └── config.json
│ ├── java
│ │ └── config.json
│ ├── launcher
│ │ └── config.json
│ └── versions
│     └── config.json
├── logs  <-----------------日志文件夹, 方便调试
└── mod  <-----------------外置mod存放文件夹, 用于存放外置mod的jar文件
    └── example-mod.jar

```