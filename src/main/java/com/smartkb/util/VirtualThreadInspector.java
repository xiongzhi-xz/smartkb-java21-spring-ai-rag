package com.smartkb.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Virtual Threads 监控工具类
 * <p>
 * 用于记录和验证 Virtual Threads 的使用情况
 * 用于记录 Virtual Threads 的实际使用效果
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
public class VirtualThreadInspector {

    /**
     * 记录当前线程信息
     *
     * @param context 上下文描述（用于标识调用位置）
     */
    public static void logThreadInfo(String context) {
        Thread thread = Thread.currentThread();
        log.info("[Virtual Threads] {} | 线程名称: {}, 是否虚拟线程: {}, 线程ID: {}",
                context,
                thread.getName(),
                thread.isVirtual() ? "✓ YES" : "✗ NO",
                thread.threadId());
    }

    /**
     * 记录当前线程信息（带额外参数）
     *
     * @param context 上下文描述
     * @param extra   额外信息（如文档数量、批次数等）
     */
    public static void logThreadInfo(String context, String extra) {
        Thread thread = Thread.currentThread();
        log.info("[Virtual Threads] {} | {} | 线程名称: {}, 是否虚拟线程: {}, 线程ID: {}",
                context,
                extra,
                thread.getName(),
                thread.isVirtual() ? "✓ YES" : "✗ NO",
                thread.threadId());
    }
}
