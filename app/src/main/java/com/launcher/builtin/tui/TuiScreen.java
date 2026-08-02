package com.launcher.builtin.tui;

import java.util.List;
import java.util.function.Consumer;

/**
 * TUI 界面数据模型
 * 任何 Mod 都可以通过构造此类来向 TuiMod 请求渲染一个界面
 */
public class TuiScreen {
    private final String title;          // 顶部边框显示的标题
    private final String menuPath;       // 当前菜单路径（如 "主菜单 > 设置"）
    private final List<String> items;    // 菜单项列表（不包含底部的返回/退出）
    private final boolean isRootMenu;    // 是否为一级菜单
    private final Consumer<Integer> onSelect; // 用户按下回车时的回调（传入选中项的索引）

    public TuiScreen(String title, String menuPath, List<String> items, boolean isRootMenu, Consumer<Integer> onSelect) {
        this.title = title;
        this.menuPath = menuPath;
        this.items = items;
        this.isRootMenu = isRootMenu;
        this.onSelect = onSelect;
    }

    public String getTitle() { return title; }
    public String getMenuPath() { return menuPath; }
    public List<String> getItems() { return items; }
    public boolean isRootMenu() { return isRootMenu; }
    public Consumer<Integer> getOnSelect() { return onSelect; }
}
