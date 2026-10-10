package net.gtn.dimensionalpocket.util;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * dimensionalpockets 统一日志工具。
 *
 * <p>规范：{@code E:\pjectAI\z3\模组日志规范.md}（v3.0 合并版：第一部强制规范 + 第二部场景库）
 *
 * <p><b>日志分级</b>：
 * <ul>
 *   <li>L1 {@link #step}  关键流程，始终 info</li>
 *   <li>L2 {@link #allow}/{@link #deny}/{@link #state} 判定结果，始终 info</li>
 *   <li>L3 {@link #trace} 细粒度，仅 trace 开关打开时按 info 输出（支持按模块开）</li>
 * </ul>
 *
 * <p><b>可观测性五件套</b>：日志（step/deny）+ 指标（{@link #metric}）+ 追踪（{@link #newTrace}）
 * + 转储（{@link #dump}）+ 断言（{@link #expect}）。
 *
 * <p><b>抗故障能力</b>：{@link #startWatchdog} 卡死检测、{@link #installShutdownHook} 崩溃自动落盘。
 *
 * <p>所有输出统一前缀 {@code [dimensionalpockets][模块][动作]}，字段一律 {@code key=value}，
 * 便于在整合包日志中 grep 及被 AI 解析。
 */
public final class ModLog {

    /** 模组 id，用于读取自身版本号 */
    public static final String MODID = "dimensionalpockets";
    /** 日志前缀，整合包日志里靠它过滤 */
    public static final String TAG = "dimensionalpockets";

    private static final Logger LOG = LogUtils.getLogger();

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 环形缓冲容量（条） */
    private static final int RING_CAPACITY = 4000;
    private static final Deque<String> RING = new ArrayDeque<>(RING_CAPACITY);
    private static final Object RING_LOCK = new Object();

    private static final AtomicLong TOTAL_LINES = new AtomicLong();
    private static final Map<String, AtomicLong> LEVEL_COUNTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> MODULE_COUNTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> MARKER_COUNTS = new ConcurrentHashMap<>();

    /** L3 细粒度日志总开关 */
    private static volatile boolean traceAll = Boolean.getBoolean("dimensionalpockets.trace");
    /** 按模块开 L3 的集合 */
    private static final Set<String> TRACE_MODULES = ConcurrentHashMap.newKeySet();

    private static final int CLIP_DEFAULT = 200;

    private ModLog() {
    }

    // ==================================================================
    // 开关
    // ==================================================================

    /** 全局 trace 是否开启（不含按模块开启的部分） */
    public static boolean isTrace() {
        return traceAll;
    }

    /** 某个模块的 trace 是否开启 */
    public static boolean isTrace(String module) {
        return traceAll || TRACE_MODULES.contains(module);
    }

    public static void setTrace(boolean on) {
        traceAll = on;
        step("ModLog", "trace", "trace_all={}", on ? "ON" : "OFF");
    }

    /** 按模块开关：整合包用户通常只关心某一个模块 */
    public static void setTrace(String module, boolean on) {
        if (on) {
            TRACE_MODULES.add(module);
        } else {
            TRACE_MODULES.remove(module);
        }
        step("ModLog", "trace", "module={} trace={} active_modules={}", module, on ? "ON" : "OFF",
                TRACE_MODULES.isEmpty() ? "(none)" : String.join(",", TRACE_MODULES));
    }

    public static boolean toggleTrace() {
        setTrace(!traceAll);
        return traceAll;
    }

    public static String traceState() {
        return "all=" + (traceAll ? "ON" : "OFF")
                + " modules=" + (TRACE_MODULES.isEmpty() ? "(none)" : String.join(",", TRACE_MODULES));
    }

    // ==================================================================
    // L1 关键流程
    // ==================================================================

    public static void step(String module, String action, String fmt, Object... args) {
        emit("INFO", "INFO", module, action, "", fmt, args, null);
    }

    // ==================================================================
    // L2 判定结果
    // ==================================================================

    /** 前置条件通过 */
    public static void allow(String module, String action, String fmt, Object... args) {
        emit("INFO", "INFO", module, action, "ALLOW", fmt, args, null);
    }

    /** 前置条件不满足导致的 return / 跳过 —— 最重要的一类日志 */
    public static void deny(String module, String action, String fmt, Object... args) {
        emit("INFO", "INFO", module, action, "DENIED", fmt, args, null);
    }

    /** 状态跃迁：state=OLD->NEW */
    public static void state(String module, String action, String fmt, Object... args) {
        emit("INFO", "INFO", module, action, "STATE", fmt, args, null);
    }

    private static final Map<String, AtomicInteger> REPEAT_COUNTS = new ConcurrentHashMap<>();

    /**
     * 连续失败的静默降噪：同一个 key 首次输出，之后每累计到 threshold 的整数倍才输出一次告警。
     *
     * <p>用于"功能持续不工作"的场景——既不刷屏，又能在玩家玩一小时后明确告诉他有问题。
     * 问题恢复时应调用 {@link #resetRepeated(String)}。
     */
    public static void denyRepeated(String key, int threshold, String module, String action,
                                    String fmt, Object... args) {
        int n = REPEAT_COUNTS.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        if (n == 1) {
            emit("INFO", "INFO", module, action, "DENIED", fmt, args, null);
        } else if (threshold > 0 && n % threshold == 0) {
            emit("WARN", "WARN", module, action, "REPEATED",
                    "repeated_deny count=" + n + " key=" + key + " " + fmt, args, null);
        }
    }

    public static void resetRepeated(String key) {
        REPEAT_COUNTS.remove(key);
    }

    // ==================================================================
    // L3 细粒度
    // ==================================================================

    public static void trace(String module, String action, String fmt, Object... args) {
        if (!isTrace(module)) {
            return;
        }
        emit("INFO", "TRACE", module, action, "", fmt, args, null);
    }

    // ==================================================================
    // 断言：只在不变量被破坏时输出
    // ==================================================================

    private static final AtomicLong EXPECT_FAILS = new AtomicLong();

    /**
     * 不变量校验。条件成立时**完全不输出**，失败时输出 {@code [EXPECT_FAIL]}。
     *
     * <p>断言只检测、不修复——修复逻辑与检测逻辑混在一起会掩盖问题。
     * 断言必须廉价（禁止世界扫描、大集合遍历）。
     */
    public static void expect(boolean condition, String module, String action, String fmt, Object... args) {
        if (condition) {
            return;
        }
        EXPECT_FAILS.incrementAndGet();
        emit("WARN", "WARN", module, action, "EXPECT_FAIL", fmt, args, null);
    }

    public static long expectFailCount() {
        return EXPECT_FAILS.get();
    }

    // ==================================================================
    // 指标：定时聚合输出，用于发现退化趋势
    // ==================================================================

    private static final long METRIC_WINDOW_MS =
            Long.getLong("dimensionalpockets.metricWindowMs", 10_000L);
    private static final Map<String, MetricAcc> METRICS = new ConcurrentHashMap<>();

    /** 记录一个数值指标。同一 module+name 在窗口内聚合，窗口结束时输出 min/max/avg */
    public static void metric(String module, String name, double value) {
        String key = module + "." + name;
        MetricAcc acc = METRICS.computeIfAbsent(key, k -> new MetricAcc());
        String out = acc.add(value);
        if (out != null) {
            emit("INFO", "METRIC", module, name, "METRIC", out, new Object[0], null);
        }
    }

    public static void metric(String module, String name, long value) {
        metric(module, name, (double) value);
    }

    private static final class MetricAcc {
        private double min = Double.MAX_VALUE;
        private double max = -Double.MAX_VALUE;
        private double sum;
        private long n;
        private long windowStart = System.currentTimeMillis();

        synchronized String add(double v) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
            n++;
            long now = System.currentTimeMillis();
            if (now - windowStart < METRIC_WINDOW_MS) {
                return null;
            }
            String out = String.format(Locale.ROOT,
                    "value=%.3f min=%.3f max=%.3f avg=%.3f n=%d window=%dms",
                    v, min, max, sum / n, n, now - windowStart);
            min = Double.MAX_VALUE;
            max = -Double.MAX_VALUE;
            sum = 0;
            n = 0;
            windowStart = now;
            return out;
        }
    }

    // ==================================================================
    // 追踪：串起一次操作跨越的多个系统
    // ==================================================================

    private static final AtomicInteger TRACE_SEQ = new AtomicInteger();

    /** 生成一个短 traceId，只应在低频入口（玩家操作/指令/网络包）调用 */
    public static String newTrace() {
        return "t" + Integer.toHexString(TRACE_SEQ.incrementAndGet() & 0xFFFFF);
    }

    public static void endTrace(String traceId, String result) {
        step("Trace", "end", "trace={} result={}", traceId, result);
    }

    // ==================================================================
    // 计时：长任务与性能
    // ==================================================================

    /** 开始计时，返回纳秒起点 */
    public static long begin() {
        return System.nanoTime();
    }

    public static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    public static long elapsedUs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000L;
    }

    /**
     * 超过阈值就告警。50ms 是 MC 一个 tick 的预算，单次超过必然掉帧，因此是默认阈值。
     */
    public static void warnIfSlow(long startNanos, long thresholdMs, String module, String action,
                                  String fmt, Object... args) {
        long ms = elapsedMs(startNanos);
        if (ms < thresholdMs) {
            return;
        }
        emit("WARN", "WARN", module, action, "SLOW",
                "took=" + ms + "ms " + fmt, args, null);
    }

    // ==================================================================
    // 异常与告警
    // ==================================================================

    /** 出错，带上下文 + 完整堆栈。替代 e.printStackTrace() */
    public static void fail(String module, String action, Throwable t, String fmt, Object... args) {
        emit("ERROR", "ERROR", module, action, "FAIL", fmt, args, t);
    }

    public static void error(String module, String action, String fmt, Object... args) {
        emit("ERROR", "ERROR", module, action, "ERROR", fmt, args, null);
    }

    public static void warn(String module, String action, String fmt, Object... args) {
        emit("WARN", "WARN", module, action, "WARN", fmt, args, null);
    }

    // ==================================================================
    // 去重与限流
    // ==================================================================

    private static final Set<String> ONCE_KEYS = ConcurrentHashMap.newKeySet();

    /** 整个会话只输出一次 */
    public static void once(String key, String module, String action, String fmt, Object... args) {
        if (ONCE_KEYS.add(key)) {
            step(module, action, fmt, args);
        }
    }

    private static final Map<String, Long> LAST_EMIT = new ConcurrentHashMap<>();

    /** 每 intervalTicks 游戏刻最多输出一次（20 tick ≈ 1 秒） */
    public static void every(String key, int intervalTicks, String module, String action, String fmt, Object... args) {
        long now = System.currentTimeMillis();
        long intervalMs = Math.max(1L, intervalTicks) * 50L;
        Long last = LAST_EMIT.get(key);
        if (last != null && now - last < intervalMs) {
            return;
        }
        LAST_EMIT.put(key, now);
        step(module, action, fmt, args);
    }

    public static void resetThrottle() {
        LAST_EMIT.clear();
        ONCE_KEYS.clear();
    }

    // ==================================================================
    // 看门狗：卡死检测
    // ==================================================================

    private static volatile long lastHeartbeatMs = System.currentTimeMillis();
    private static volatile long lastHeartbeatTick = -1;
    private static volatile String lastHeartbeatName = "(never)";
    private static volatile String lastExecutionPoint = "(none)";
    private static volatile long lastExecutionMs = System.currentTimeMillis();
    private static volatile Thread watchdogThread;
    private static volatile long watchdogThresholdMs = 10_000L;
    /** 卡死报告的降噪：首次立即报，之后每 30 秒补报一次（否则每秒 25 行线程栈会刷爆日志） */
    private static final long STALL_REPORT_MIN_INTERVAL_MS = 30_000L;
    private static volatile long lastStallReportMs;
    private static final AtomicInteger STALL_REPORTS = new AtomicInteger();

    /**
     * 主线程每 tick 调用一次。卡死时看门狗靠它判断"主线程是否还在跑"。
     *
     * @param name 心跳名，如 "server-tick" / "client-tick"
     * @param tick 当前游戏刻
     */
    public static void heartbeat(String name, long tick) {
        // 从卡死中恢复：记录一次，便于知道卡了多久、报了几次
        if (STALL_REPORTS.get() > 0) {
            step("Watchdog", "recovered", "stall_ended reports={} silent_before={}ms",
                    STALL_REPORTS.get(), System.currentTimeMillis() - lastHeartbeatMs);
            STALL_REPORTS.set(0);
            lastStallReportMs = 0L;
        }
        lastHeartbeatMs = System.currentTimeMillis();
        lastHeartbeatTick = tick;
        lastHeartbeatName = name;
    }

    /**
     * 启动看门狗守护线程。
     *
     * <p>卡死时日志会完全停止输出，此时唯一能提供信息的机制就是这个独立线程。
     * 它会在主线程超时后输出：最后心跳、**最后执行的 dimensionalpockets 代码位置**、主线程栈。
     *
     * @param stallThresholdMs 超过多久无心跳判定为卡死，默认 10 秒
     */
    public static void startWatchdog(long stallThresholdMs) {
        watchdogThresholdMs = Math.max(1000L, stallThresholdMs);
        if (watchdogThread != null) {
            return;
        }
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long silent = System.currentTimeMillis() - lastHeartbeatMs;
                if (silent < watchdogThresholdMs) {
                    continue;
                }
                reportStall(silent);
            }
        }, TAG + "-watchdog");
        t.setDaemon(true);
        watchdogThread = t;
        t.start();
        step("Watchdog", "start", "threshold={}ms thread={}", watchdogThresholdMs, t.getName());
    }

    private static void reportStall(long silentMs) {
        // 先快照，再输出：看门狗自己的 emit 会经过 touch()，不能让它覆盖掉要诊断的信息
        String code = lastExecutionPoint;
        long codeAgo = System.currentTimeMillis() - lastExecutionMs;

        long now = System.currentTimeMillis();
        if (STALL_REPORTS.get() > 0 && now - lastStallReportMs < STALL_REPORT_MIN_INTERVAL_MS) {
            return;
        }
        lastStallReportMs = now;
        int n = STALL_REPORTS.incrementAndGet();

        emit("ERROR", "ERROR", "Watchdog", "STALL", "",
                "server_stalled silent={}ms report#{} last_heartbeat={} tick={}",
                new Object[]{silentMs, n, lastHeartbeatName, lastHeartbeatTick}, null);
        emit("ERROR", "ERROR", "Watchdog", "STALL", "",
                "last_dimensionalpockets_code={} ({}ms ago)",
                new Object[]{code, codeAgo}, null);
        for (String frame : captureMainStack()) {
            emit("ERROR", "ERROR", "Watchdog", "STALL", "", "  at {}", new Object[]{frame}, null);
        }
    }

    private static List<String> captureMainStack() {
        List<String> out = new ArrayList<>();
        try {
            for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                String name = e.getKey().getName();
                if (!name.contains("Server thread") && !name.contains("Render thread") && !name.equals("main")) {
                    continue;
                }
                out.add("--- " + name + " ---");
                StackTraceElement[] st = e.getValue();
                for (int i = 0; i < st.length && i < 25; i++) {
                    out.add(st[i].toString());
                }
            }
        } catch (Throwable ignored) {
            // 看门狗本身不能因为采集失败而中断
        }
        if (out.isEmpty()) {
            out.add("(未能采集到主线程栈)");
        }
        return out;
    }

    public static String watchdogState() {
        return "threshold=" + watchdogThresholdMs + "ms"
                + " silent=" + (System.currentTimeMillis() - lastHeartbeatMs) + "ms"
                + " last_code=" + lastExecutionPoint;
    }

    // ==================================================================
    // 崩溃自动落盘
    // ==================================================================

    private static volatile boolean shutdownHookInstalled;

    /**
     * 注册 JVM 关闭钩子：崩溃或退出时自动把环形缓冲写盘。
     *
     * <p>关闭钩子里只能写内存中已有的内容——不能访问世界、注册表或做 IO 之外的重活。
     * ModLog 的环形缓冲预存的是格式化完成的文本，所以这条路径是安全的。
     */
    public static void installShutdownHook() {
        if (shutdownHookInstalled) {
            return;
        }
        shutdownHookInstalled = true;
        Thread hook = new Thread(() -> {
            try {
                Path p = dumpQuiet();
                if (p != null) {
                    System.out.println("[" + TAG + "] shutdown dump -> " + p.toAbsolutePath());
                }
            } catch (Throwable ignored) {
                // 关闭期间不能抛异常
            }
        }, TAG + "-shutdown-dump");
        try {
            Runtime.getRuntime().addShutdownHook(hook);
            step("ModLog", "shutdown_hook", "installed");
        } catch (Throwable t) {
            fail("ModLog", "shutdown_hook", t, "install_failed");
        }
    }

    // ==================================================================
    // 上下文辅助方法
    // ==================================================================

    public static String pos(BlockPos p) {
        return p == null ? "pos=null" : "pos=(" + p.getX() + "," + p.getY() + "," + p.getZ() + ")";
    }

    public static String exact(Vec3 v) {
        if (v == null) {
            return "exact=null";
        }
        return String.format(Locale.ROOT, "exact=(%.1f,%.1f,%.1f)", v.x, v.y, v.z);
    }

    public static String exact(Entity e) {
        return e == null ? "exact=null" : exact(e.position());
    }

    public static String dim(Level level) {
        return level == null ? "dim=null" : "dim=" + level.dimension().location();
    }

    public static String side(Level level) {
        if (level == null) {
            return "side=null";
        }
        return "side=" + (level.isClientSide ? "CLIENT" : "SERVER");
    }

    public static String player(Player p) {
        if (p == null) {
            return "player=null";
        }
        String uuid = p.getStringUUID();
        if (uuid != null && uuid.length() > 8) {
            uuid = uuid.substring(0, 8);
        }
        return "player=" + p.getName().getString() + " uuid=" + uuid;
    }

    public static String item(ItemStack stack) {
        if (stack == null) {
            return "item=null";
        }
        if (stack.isEmpty()) {
            return "item=EMPTY";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        StringBuilder sb = new StringBuilder("item=").append(id).append(" x").append(stack.getCount());
        if (stack.hasTag() && stack.getTag() != null && !stack.getTag().isEmpty()) {
            sb.append(" nbt=").append(clip(stack.getTag().toString(), 120));
        }
        return sb.toString();
    }

    public static String block(Level level, BlockPos p) {
        if (level == null || p == null) {
            return "block=null";
        }
        BlockState state = level.getBlockState(p);
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return "block=" + id + " " + st(state);
    }

    public static String entity(Entity e) {
        if (e == null) {
            return "entity=null";
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return "entity=" + id + "#" + e.getId();
    }

    /** 超长内容截断，避免整段 NBT 把日志撑爆 */
    public static String clip(String s) {
        return clip(s, CLIP_DEFAULT);
    }

    public static String clip(String s, int max) {
        if (s == null) {
            return "null";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...(+" + (s.length() - max) + ")";
    }

    /**
     * 玩家可控字符串进日志前的清洗。
     *
     * <p>玩家自定义名称里可以塞换行符，从而在日志里伪造出一行假的日志。
     * 任何来自玩家输入的字符串（物品名、告示牌、聊天、书）进日志前都要过这一层。
     */
    public static String sanitize(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(Math.min(s.length(), CLIP_DEFAULT));
        for (int i = 0; i < s.length() && sb.length() < CLIP_DEFAULT; i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') {
                sb.append('_');
            } else if (c == '\t') {
                sb.append(' ');
            } else if (Character.isISOControl(c)) {
                sb.append('?');
            } else {
                sb.append(c);
            }
        }
        if (s.length() > sb.length()) {
            sb.append("...(+").append(s.length() - sb.length()).append(')');
        }
        return sb.toString();
    }

    // ==================================================================
    // 方块链条专用辅助（规范 §5.1）
    // 字段名与规范 §5.1 的表严格一致：face / st / hit / hitrel / shape / shapeOf / poses / seq
    // ==================================================================

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final Map<String, AtomicInteger> SEQ_CHANNELS = new ConcurrentHashMap<>();

    /** 方向 / 方块面 → {@code face=UP} */
    public static String face(Direction d) {
        return d == null ? "face=null" : "face=" + d;
    }

    /** 完整方块状态 → {@code st=dimensionalpockets:conveyor[facing=north,powered=true]} */
    public static String st(BlockState state) {
        if (state == null) {
            return "st=null";
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        StringBuilder sb = new StringBuilder("st=").append(id);
        if (!state.getValues().isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Map.Entry<Property<?>, Comparable<?>> e : state.getValues().entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(e.getKey().getName()).append('=').append(e.getValue());
                first = false;
            }
            sb.append(']');
        }
        return clip(sb.toString(), 140);
    }

    /** 方块状态跃迁 → {@code st=OLD->NEW}；两者相同返回 null（调用方跳过输出即可） */
    public static String st(BlockState before, BlockState after) {
        if (before == null || after == null) {
            return "st=null";
        }
        if (before == after) {
            return null;
        }
        return "st=" + clip(rawSt(before), 60) + "->" + clip(rawSt(after), 60);
    }

    private static String rawSt(BlockState state) {
        String s = st(state);
        return s.startsWith("st=") ? s.substring(3) : s;
    }

    /** 命中点：世界坐标 + 方块内相对坐标（0~1）。多部分方块靠 hitrel 判断点到哪一半 */
    public static String hit(Vec3 v, BlockPos pos) {
        if (v == null || pos == null) {
            return "hit=null";
        }
        return String.format(Locale.ROOT,
                "hit=(%.2f,%.2f,%.2f) hitrel=(%.2f,%.2f,%.2f)",
                v.x, v.y, v.z, v.x - pos.getX(), v.y - pos.getY(), v.z - pos.getZ());
    }

    /** 包围盒 → {@code box=(0.000,0.000,0.000)-(1.000,0.500,1.000)} */
    public static String box(AABB b) {
        return b == null ? "box=null" : "box=" + boxStr(b);
    }

    private static String boxStr(AABB b) {
        return String.format(Locale.ROOT, "(%.3f,%.3f,%.3f)-(%.3f,%.3f,%.3f)",
                b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    /**
     * 碰撞箱 / 视觉箱 / 遮挡箱 / 交互箱 / 支撑箱 → {@code shape=(...)-(...) parts=N empty=false}
     *
     * <p>{@code shape=EMPTY} 是"看得见摸不着 / 点不到"的直接线索（规范 §5.9）。
     */
    public static String shape(VoxelShape s) {
        if (s == null) {
            return "shape=null";
        }
        return s.isEmpty()
                ? "shape=EMPTY parts=0 empty=true"
                : "shape=" + boxStr(s.bounds()) + " parts=" + s.toAabbs().size() + " empty=false";
    }

    /** 带形状名的输出 → {@code shapeOf=collision shape=(...)-(...)}。五个形状混用时靠它区分 */
    public static String shapeOf(String which, VoxelShape s) {
        return "shapeOf=" + which + " " + shape(s);
    }

    /** 坐标集合（自动截断到 8 个）→ {@code poses=[(12,64,-3),...] n=10} */
    public static String poses(Collection<BlockPos> list) {
        if (list == null) {
            return "poses=null n=0";
        }
        StringBuilder sb = new StringBuilder("poses=[");
        int i = 0;
        for (BlockPos p : list) {
            if (i >= 8) {
                sb.append(",+").append(list.size() - 8);
                break;
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append('(').append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append(')');
            i++;
        }
        return sb.append("] n=").append(list.size()).toString();
    }

    /** 网络包序号 → 用 {@code seq=N} 字段打出去。日志里出现跳跃/重复即丢包或重发（规范 §21.6） */
    public static int nextSeq() {
        return SEQ.incrementAndGet();
    }

    /** 按通道分开的序号，避免多个包类型互相干扰 */
    public static int nextSeq(String channel) {
        return SEQ_CHANNELS.computeIfAbsent(channel == null ? "default" : channel,
                k -> new AtomicInteger()).incrementAndGet();
    }

    // ==================================================================
    // 启动横幅
    // ==================================================================

    public static void banner() {
        banner(Map.of());
    }

    /**
     * 启动横幅：把定位问题所需的全部环境信息一次打全。
     *
     * @param keyDeps 需要重点关注的依赖模组，键为 modId。实际版本从 ModList 读取。
     */
    public static void banner(Map<String, String> keyDeps) {
        String modVer = modVersion(MODID);
        int modCount = -1;
        try {
            modCount = ModList.get().size();
        } catch (Throwable ignored) {
            // ModList 尚未初始化
        }

        rule();
        line("  " + TAG + " " + modVer + "  |  Minecraft " + mcVersion() + "  |  Forge " + forgeVersion());
        line("  env=" + (isDev() ? "DEV" : "PROD")
                + "  java=" + System.getProperty("java.version", "?")
                + "  os=" + System.getProperty("os.name", "?")
                + "  maxMem=" + (Runtime.getRuntime().maxMemory() / (1024L * 1024L)) + "MB"
                + "  thread=" + Thread.currentThread().getName());
        line("  加载模组数: " + (modCount < 0 ? "?" : String.valueOf(modCount)));
        if (!keyDeps.isEmpty()) {
            line("  关键依赖:");
            for (String id : keyDeps.keySet()) {
                String v = modVersion(id);
                line(String.format(Locale.ROOT, "    %-16s %-12s %s", id, v,
                        "unknown".equals(v) ? "MISSING" : "LOADED"));
            }
        }
        rule();
    }

    public static void bannerLine(String text) {
        line("  " + text);
    }

    private static void rule() {
        raw("=".repeat(60));
    }

    private static void line(String text) {
        raw(text);
    }

    private static void raw(String text) {
        String out = "[" + TAG + "] " + text;
        LOG.info(out);
        record("INFO", out);
    }

    // ==================================================================
    // 飞行记录仪：导出
    // ==================================================================

    /** 导出全部环形缓冲 */
    public static Path dump() {
        return dump(Path.of("logs"), -1, null);
    }

    public static Path dump(Path logsDir) {
        return dump(logsDir, -1, null);
    }

    /** 只导出最后 lastN 条 */
    public static Path dumpLast(Path logsDir, int lastN) {
        return dump(logsDir, lastN, null);
    }

    /** 只导出某个模块的日志 */
    public static Path dumpModule(Path logsDir, String module) {
        return dump(logsDir, -1, module);
    }

    private static Path dump(Path logsDir, int lastN, String moduleFilter) {
        Path out = dumpTo(logsDir, lastN, moduleFilter);
        if (out != null) {
            step("ModLog", "dump", "exported={} lines={}", out.toAbsolutePath(), ringSize());
        }
        return out;
    }

    /** 静默导出（供关闭钩子使用，不产生新日志） */
    private static Path dumpQuiet() {
        return dumpTo(Path.of("logs"), -1, null);
    }

    private static Path dumpTo(Path logsDir, int lastN, String moduleFilter) {
        String name = TAG + "-trace-" + LocalDateTime.now().format(FILE_TS) + ".log";
        Path out = logsDir.resolve(name);
        try {
            Files.createDirectories(logsDir);
            List<String> lines = new ArrayList<>();
            synchronized (RING_LOCK) {
                for (String l : RING) {
                    if (moduleFilter != null && !l.contains("[" + TAG + "][" + moduleFilter + "]")) {
                        continue;
                    }
                    lines.add(l);
                }
            }
            if (lastN > 0 && lines.size() > lastN) {
                lines = lines.subList(lines.size() - lastN, lines.size());
            }

            StringBuilder sb = new StringBuilder(1 << 16);
            sb.append("# ").append(TAG).append(" trace dump")
                    .append(" | lines=").append(lines.size())
                    .append(" | total=").append(TOTAL_LINES.get())
                    .append(" | ").append(LocalDateTime.now())
                    .append(System.lineSeparator());
            // 文件头也带环境信息：日志文件会脱离游戏单独流转
            sb.append("# ver=").append(modVersion(MODID))
                    .append(" mc=").append(mcVersion())
                    .append(" forge=").append(forgeVersion())
                    .append(" env=").append(isDev() ? "DEV" : "PROD")
                    .append(" trace=").append(traceState())
                    .append(System.lineSeparator());
            if (moduleFilter != null) {
                sb.append("# filter=module:").append(moduleFilter).append(System.lineSeparator());
            }
            for (String l : lines) {
                sb.append(l).append(System.lineSeparator());
            }
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
            return out;
        } catch (IOException e) {
            // 关闭期间不能抛异常，这里只用 LOG 记录（不经过环形缓冲）
            LOG.error("[{}][ModLog][dump][FAIL] export_failed target={}", TAG, out.toAbsolutePath(), e);
            return null;
        }
    }

    public static int ringSize() {
        synchronized (RING_LOCK) {
            return RING.size();
        }
    }

    public static void clearRing() {
        synchronized (RING_LOCK) {
            RING.clear();
        }
        TOTAL_LINES.set(0);
        LEVEL_COUNTS.clear();
        MODULE_COUNTS.clear();
        MARKER_COUNTS.clear();
        EXPECT_FAILS.set(0);
        step("ModLog", "clear", "ring_buffer_cleared");
    }

    /** 状态行，供 /dimensionalpockets debug logs status 回显 */
    public static String statusLine() {
        return "trace[" + traceState() + "]"
                + " buffered=" + ringSize() + "/" + RING_CAPACITY
                + " total=" + TOTAL_LINES.get()
                + " ERROR=" + count(LEVEL_COUNTS, "ERROR")
                + " WARN=" + count(LEVEL_COUNTS, "WARN")
                + " DENIED=" + count(MARKER_COUNTS, "DENIED")
                + " EXPECT_FAIL=" + EXPECT_FAILS.get();
    }

    /**
     * 摘要：大日志先看摘要再决定读哪段。
     * 输出总行数、模块分布 top10、结果标记分布。
     */
    public static String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("total=").append(TOTAL_LINES.get());
        sb.append(" modules[");
        MODULE_COUNTS.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(10)
                .forEach(e -> sb.append(' ').append(e.getKey()).append('=').append(e.getValue().get()));
        sb.append(" ]");
        sb.append(" results[");
        MARKER_COUNTS.forEach((k, v) -> sb.append(' ').append(k).append('=').append(v.get()));
        sb.append(" ]");
        return sb.toString();
    }

    private static void logSummary() {
        step("ModLog", "summary", "{}", summary());
    }

    // ==================================================================
    // 内部实现
    // ==================================================================

    private static void emit(String logLevel, String ringLevel, String module, String action,
                             String marker, String fmt, Object[] args, Throwable t) {
        touch(module, action);

        String prefix = "[" + TAG + "][" + module + "][" + action + "]"
                + (marker.isEmpty() ? "" : "[" + marker + "]") + " ";
        // 控制台与环形缓冲走同一份文本，保证导出的日志和屏幕上看到的完全一致
        String text = prefix + safeFormat(fmt, args);

        if (t == null) {
            switch (logLevel) {
                case "ERROR" -> LOG.error(text);
                case "WARN" -> LOG.warn(text);
                default -> LOG.info(text);
            }
        } else {
            // Throwable 作为末参，SLF4J 才会打印完整堆栈
            LOG.error(text, t);
        }

        record(ringLevel, text);
        bump(LEVEL_COUNTS, ringLevel);
        bump(MODULE_COUNTS, module);
        if (!marker.isEmpty()) {
            bump(MARKER_COUNTS, marker);
        }
    }

    /** 记录最后执行点，供看门狗在卡死时报告 */
    private static void touch(String module, String action) {
        if (Thread.currentThread() == watchdogThread) {
            // 看门狗自己的诊断输出不能污染"最后执行点"——那正是它要报告的内容
            return;
        }
        lastExecutionPoint = "[" + module + "][" + action + "]";
        lastExecutionMs = System.currentTimeMillis();
    }

    /**
     * 手动替换 {} 占位符，产出给环形缓冲用的成品文本。
     * 不用 String.format：日志正文里出现 % 时 format 会抛异常，且参数数量不匹配时会直接炸掉游戏逻辑。
     */
    private static String safeFormat(String fmt, Object[] args) {
        if (fmt == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return fmt;
        }
        StringBuilder sb = new StringBuilder(fmt.length() + 32);
        int argIdx = 0;
        int from = 0;
        while (from < fmt.length()) {
            int at = fmt.indexOf("{}", from);
            if (at < 0 || argIdx >= args.length) {
                break;
            }
            sb.append(fmt, from, at);
            sb.append(args[argIdx] == null ? "null" : String.valueOf(args[argIdx]));
            argIdx++;
            from = at + 2;
        }
        sb.append(fmt, from, fmt.length());
        // 参数给多了：追加出来而不是丢弃，方便发现埋点写错
        for (int i = argIdx; i < args.length; i++) {
            sb.append(" [extra:").append(args[i]).append(']');
        }
        return sb.toString();
    }

    private static void bump(Map<String, AtomicLong> map, String key) {
        map.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    private static long count(Map<String, AtomicLong> map, String key) {
        AtomicLong v = map.get(key);
        return v == null ? 0L : v.get();
    }

    private static void record(String level, String text) {
        String stamp = LocalDateTime.now().format(TS)
                + " [" + Thread.currentThread().getName() + "] "
                + level + " " + text;
        synchronized (RING_LOCK) {
            if (RING.size() >= RING_CAPACITY) {
                RING.pollFirst();
            }
            RING.addLast(stamp);
        }
        TOTAL_LINES.incrementAndGet();
    }

    // ==================================================================
    // 环境探测
    // ==================================================================

    public static String modVersion(String modId) {
        try {
            return ModList.get().getModContainerById(modId)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    /** 是否开发环境（runClient/runServer）而非发布版 */
    public static boolean isDev() {
        try {
            Class<?> c = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Object prod = c.getMethod("isProduction").invoke(null);
            return prod instanceof Boolean b && !b;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static String mcVersion() {
        try {
            return net.minecraft.SharedConstants.getCurrentVersion().getName();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String forgeVersion() {
        try {
            Class<?> c = Class.forName("net.minecraftforge.versions.forge.ForgeVersion");
            Object v = c.getMethod("getVersion").invoke(null);
            return String.valueOf(v);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    /** 供命令使用的快捷汇总（模块 -> 行数），保持插入顺序以便阅读 */
    public static Map<String, Long> moduleCounts() {
        Map<String, Long> out = new LinkedHashMap<>();
        MODULE_COUNTS.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .forEach(e -> out.put(e.getKey(), e.getValue().get()));
        return out;
    }

    /** 内部使用：把摘要写进日志（供命令调用） */
    public static void printSummary() {
        logSummary();
    }
}
