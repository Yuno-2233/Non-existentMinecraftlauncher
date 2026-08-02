package com.launcher.core;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventBus {

    // 核心存储：Key 是事件类型，Value 是该事件的所有监听器包装类
    private final Map<Class<? extends Event>, List<EventListener>> listenerMap = new ConcurrentHashMap<>();

    /**
     * 注册一个对象中的所有事件监听器
     */
    public void register(Object target) {
        Class<?> clazz = target.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribeEvent.class)) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                // Forge 规范：监听方法必须有且仅有一个参数，且该参数必须是 Event 的子类
                if (parameterTypes.length == 1 && Event.class.isAssignableFrom(parameterTypes[0])) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Event> eventType = (Class<? extends Event>) parameterTypes[0];
                    
                    // 暂时使用默认优先级 0
                    int priorityValue = 0; 

                    method.setAccessible(true);
                    EventListener listener = new EventListener(target, method, priorityValue);

                    listenerMap.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
                    // 按优先级排序（数字越小优先级越高）
                    listenerMap.get(eventType).sort(Comparator.comparingInt(l -> l.priority));
                    
                    System.out.println("  ↳ 注册事件监听器: " + clazz.getSimpleName() + "#" + method.getName() + " -> " + eventType.getSimpleName());
                }
            }
        }
    }

    /**
     * 发布事件，触发所有注册的监听器
     */
    public void post(Event event) {
        List<EventListener> listeners = listenerMap.get(event.getClass());
        if (listeners == null || listeners.isEmpty()) return;

        for (EventListener listener : listeners) {
            try {
                listener.method.invoke(listener.instance, event);
            } catch (Exception e) {
                System.err.println("✘ 事件分发失败: " + listener.method.getName());
                e.printStackTrace();
            }
        }
    }

    /**
     * 内部类：封装事件监听器
     */
    private static class EventListener {
        final Object instance;
        final Method method;
        final int priority;

        EventListener(Object instance, Method method, int priority) {
            this.instance = instance;
            this.method = method;
            this.priority = priority;
        }
    }
}
