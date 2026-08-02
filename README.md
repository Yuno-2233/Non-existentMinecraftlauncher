Non-existent Minecraft Launcher

一个轻量级、模块化的 Minecraft 启动器引擎（1.0 核心版）。


项目简介

Non-existent Minecraft Launcher 是一个基于 Node.js 开发的 Minecraft 启动器核心引擎。目前处于 1.0 版本，专注于搭建稳定、可扩展的底层架构。

当前特性：

极简的终端用户界面 (TUI)

完善的国际化 (I18n) 支持，支持动态切换语言

模块化的引擎架构，为未来的 Mod 管理和版本控制预留接口


快速开始


环境要求

Node.js (建议 v16 或以上版本)

Git


安装与运行

克隆本仓库到本地：
git clone https://github.com/Yuno-2232/Non-existentMinecraftlauncher.git

进入项目目录：
cd Non-existentMinecraftlauncher

运行启动器引擎：
node main.js


源码架构

项目的核心代码位于 core/ 目录下，采用模块化设计：

Engine.js: 核心引擎，负责 TUI 渲染、用户输入处理和状态管理。

I18n.js: 国际化模块，负责多语言文本的加载、切换和翻译。

Components.js: UI 组件库，提供绘制终端边框、菜单等基础组件。

Logger.js: 日志系统，记录引擎运行状态。


如何贡献

我们欢迎任何形式的贡献！如果你发现了 Bug 或者有新的想法，请按照以下步骤操作：

Fork 本仓库。

创建你的功能分支 (git checkout -b feature/AmazingFeature)。

提交你的修改 (git commit -m 'Add some AmazingFeature')。

推送到分支 (git push origin feature/AmazingFeature)。

打开一个 Pull Request 等待审查。


开源许可

本项目基于 MIT License 开源。