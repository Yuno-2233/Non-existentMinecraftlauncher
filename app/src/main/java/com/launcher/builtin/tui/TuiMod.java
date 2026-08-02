package com.launcher.builtin.tui;

import com.launcher.core.InitializeEvent;
import com.launcher.core.SubscribeEvent;
import com.launcher.log.LogManager;
import com.launcher.log.Logger;
import com.launcher.mod.LauncherMod;
import com.launcher.mod.ModEventBusSubscriber;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@LauncherMod("builtin-tui")
@ModEventBusSubscriber
public class TuiMod {

    private static final Logger LOGGER = LogManager.getLogger(TuiMod.class);
    private Terminal terminal;
    
    private TuiScreen currentScreen;
    private int selectedIndex = 0;
    private String searchKeyword = "";
    private List<String> filteredItems = new ArrayList<>();

    @SubscribeEvent
    public void onInitialize(InitializeEvent event) {
        LOGGER.info("[TuiMod] 收到初始化事件，正在启动 TUI 界面...");
        try {
            this.terminal = TerminalBuilder.builder().system(true).build();

            // 初始化一个空的菜单，只有底部的“退出启动器”
            List<String> emptyItems = new ArrayList<>();
            
            openScreen(new TuiScreen(
                    "Non-existent Launcher",
                    "主菜单",
                    emptyItems,
                    true,
                    index -> {
                        // 空菜单只有底部固定项，点击即退出
                        currentScreen = null;
                    }
            ));

        } catch (Exception e) {
            LOGGER.error("[TuiMod] TUI 启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 【核心扩展接口】其他 Mod 调用此方法来切换 TUI 界面
     */
    public void openScreen(TuiScreen screen) {
        this.currentScreen = screen;
        this.selectedIndex = 0;
        this.searchKeyword = "";
        this.filteredItems = new ArrayList<>(screen.getItems());
        renderLoop();
    }

    /**
     * JLine 渲染与按键处理主循环
     */
    private void renderLoop() {
        Attributes originalAttributes = null;
        try {
            originalAttributes = terminal.getAttributes();
            terminal.enterRawMode();
            
            while (currentScreen != null) {
                drawScreen();

                int key = terminal.reader().read();

                // 1. 处理 ESC 键 (返回或退出)
                if (key == '\033') {
                    if (terminal.reader().peek(1) == '[') {
                        terminal.reader().read(); // 消费掉 '['
                        int arrow = terminal.reader().read();
                        if (arrow == 'A') { // ArrowUp
                            selectedIndex = Math.max(0, selectedIndex - 1);
                        } else if (arrow == 'B') { // ArrowDown
                            int totalItems = filteredItems.size() + 1;
                            selectedIndex = Math.min(totalItems - 1, selectedIndex + 1);
                        }
                    } else {
                        // 纯 ESC 键
                        if (currentScreen.isRootMenu()) {
                            currentScreen = null; 
                        }
                    }
                } 
                // 2. 处理 Enter 键
                else if (key == '\n' || key == '\r') {
                    if (selectedIndex == filteredItems.size()) {
                        if (currentScreen.isRootMenu()) {
                            currentScreen = null; // 退出
                        } else {
                            LOGGER.info("返回上级菜单");
                        }
                    } else {
                        if (currentScreen.getOnSelect() != null) {
                            currentScreen.getOnSelect().accept(selectedIndex);
                        }
                    }
                } 
                // 3. 处理 Backspace 键
                else if (key == 127 || key == 8) {
                    if (!searchKeyword.isEmpty()) {
                        searchKeyword = searchKeyword.substring(0, searchKeyword.length() - 1);
                        filterItems();
                    }
                } 
                // 4. 处理普通可打印字符 (搜索)
                else if (key >= 32 && key < 127) {
                    searchKeyword += (char) key;
                    filterItems();
                }
            }
        } catch (IOException e) {
            // 读取失败则退出循环
        } finally {
            if (originalAttributes != null) {
                try {
                    terminal.setAttributes(originalAttributes);
                } catch (Exception ignored) {}
            }
        }
    }

    private void filterItems() {
        filteredItems.clear();
        if (searchKeyword.isEmpty()) {
            filteredItems.addAll(currentScreen.getItems());
        } else {
            for (String item : currentScreen.getItems()) {
                if (item.toLowerCase().contains(searchKeyword.toLowerCase())) {
                    filteredItems.add(item);
                }
            }
        }
        selectedIndex = 0;
    }

    /**
     * 纯 ANSI 渲染逻辑
     */
    private void drawScreen() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[2J\033[H");

        // 1. 顶部边框与名称
        String title = currentScreen.getTitle();
        sb.append("\033[36;1m"); // 青色加粗
        sb.append("╔").append("═".repeat(Math.max(0, title.length() + 2))).append("╗\n");
        sb.append("║ ").append(title).append(" ║\n");
        sb.append("╚").append("═".repeat(Math.max(0, title.length() + 2))).append("╝\n");
        sb.append("\033[0m");

        // 2. 当前菜单路径
        sb.append("\033[33m"); // 黄色
        sb.append(">> 当前位置: ").append(currentScreen.getMenuPath()).append("\n");
        sb.append("\033[0m");

        // 3. 置顶搜索框
        sb.append("\033[32m"); // 绿色
        sb.append(">> 搜索: ").append(searchKeyword).append("_\n");
        sb.append("\033[0m");
        sb.append("────────────────────────────\n");

        // 4. 菜单列表
        for (int i = 0; i < filteredItems.size(); i++) {
            if (i == selectedIndex) {
                sb.append("\033[32;1m"); // 绿色加粗
                sb.append(" > ").append(filteredItems.get(i)).append("\n");
                sb.append("\033[0m");
            } else {
                sb.append("   ").append(filteredItems.get(i)).append("\n");
            }
        }

        // 5. 底部固定项（始终在末尾）
        String bottomItem = currentScreen.isRootMenu() ? "退出启动器" : "返回上级菜单";
        if (selectedIndex == filteredItems.size()) {
            sb.append("\033[32;1m"); // 绿色加粗
            sb.append(" > ").append(bottomItem).append("\n");
            sb.append("\033[0m");
        } else {
            sb.append("   ").append(bottomItem).append("\n");
        }

        terminal.writer().print(sb.toString());
        terminal.writer().flush();
    }
}
