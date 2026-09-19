package com.risingcrescent.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.risingcrescent.audit.AuditService;
import com.risingcrescent.ops.MemoryLogBuffer.LogLine;
import com.risingcrescent.service.StripeGateway;
import com.zaxxer.hikari.HikariDataSource;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpsService {
    private static final Map<String, String> GC_PROFILES = Map.of(
            "balanced", "-Xms256m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=200",
            "low-pause", "-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+ParallelRefProcEnabled",
            "throughput", "-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=500 -XX:G1ReservePercent=10",
            "constrained", "-Xms128m -Xmx384m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
    );

    private final DataSource dataSource;
    private final StripeGateway stripe;
    private final MemoryLogBuffer logs;
    private final AuditService audit;
    private final ApplicationContext ctx;
    private final ObjectMapper objectMapper;

    @Value("${app.upload-dir}")
    private String uploadDir;
    @Value("${app.kafka.enabled:false}")
    private boolean kafkaEnabled;
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrap;
    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    private final AtomicReference<Recording> recording = new AtomicReference<>();
    private final ScheduledExecutorService restartPool = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ops-restart");
        t.setDaemon(true);
        return t;
    });

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> jvm = jvmStats();
        Map<String, Object> db = dbCheck();
        Map<String, Object> stripeStatus = stripe.probe();
        Map<String, Object> network = networkChecks();
        List<Map<String, Object>> findings = findings(jvm, db, stripeStatus, network);
        out.put("checkedAt", Instant.now().toString());
        out.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        out.put("pid", ManagementFactory.getRuntimeMXBean().getPid());
        out.put("javaVersion", Runtime.version().toString());
        out.put("jvmArgs", ManagementFactory.getRuntimeMXBean().getInputArguments());
        out.put("pendingJvmOptions", readJvmOptions());
        out.put("gcProfiles", GC_PROFILES);
        out.put("jvm", jvm);
        out.put("database", db);
        out.put("kafka", kafkaCheck());
        out.put("stripe", stripeStatus);
        out.put("network", network);
        out.put("profiling", profileStatus());
        out.put("findings", findings);
        out.put("restartHint", "Restart exits this API process. Docker or systemd will bring it back if they are watching it.");
        return out;
    }

    public Map<String, Object> logView(int limit, String query, Long afterId) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<LogLine> rows = logs.recent(limit, query, afterId);
        m.put("cursor", logs.cursor());
        m.put("lines", rows);
        return m;
    }

    public Map<String, Object> runAction(String action, String profile, String actor) {
        String a = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> result = switch (a) {
            case "gc", "run-gc" -> forceGc();
            case "thread-dump" -> Map.of("ok", true, "output", threadDump());
            case "profile-start" -> startProfile();
            case "profile-stop" -> stopProfile();
            case "reconnect-stripe", "restart-stripe" -> reconnectStripe();
            case "apply-gc-profile" -> applyGcProfile(profile);
            case "retry-network" -> networkChecks();
            case "restart-api", "restart-backend" -> scheduleRestart(actor);
            default -> throw new IllegalArgumentException("Unknown ops action.");
        };
        audit.record(actor, "OPS_" + a.toUpperCase(Locale.ROOT), "Ops", a, profile == null ? "" : profile);
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("ok", result.getOrDefault("ok", true));
        wrapped.put("action", a);
        wrapped.putAll(result);
        return wrapped;
    }

    public Map<String, Object> terminal(String raw, String actor) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty()) {
            return Map.of("output", "");
        }
        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        switch (cmd) {
            case "help" -> out.append("""
                    Commands:
                      help
                      health
                      cpu
                      gc [run|profile <name>]
                      stripe
                      ping [db|kafka|stripe|all]
                      logs [n] [filter]
                      threads
                      profile [start|status|stop]
                      restart api
                      restart stripe
                    """);
            case "health" -> out.append(pretty(snapshotSummary()));
            case "cpu" -> out.append(pretty(jvmStats()));
            case "stripe" -> out.append(pretty(stripe.probe()));
            case "threads" -> out.append(threadDump());
            case "logs" -> {
                int n = 80;
                String filter = "";
                if (parts.length > 1 && parts[1].matches("\\d+")) {
                    n = Integer.parseInt(parts[1]);
                    if (parts.length > 2) filter = line.substring(line.indexOf(parts[2]));
                } else if (parts.length > 1) {
                    filter = line.substring(line.indexOf(parts[1]));
                }
                for (LogLine l : logs.recent(n, filter, null)) {
                    out.append(formatLog(l)).append('\n');
                }
            }
            case "ping" -> {
                String target = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "all";
                out.append(pretty(ping(target)));
            }
            case "gc" -> {
                if (parts.length > 1 && "run".equalsIgnoreCase(parts[1])) {
                    out.append(pretty(runAction("gc", null, actor)));
                } else if (parts.length > 2 && "profile".equalsIgnoreCase(parts[1])) {
                    out.append(pretty(runAction("apply-gc-profile", parts[2], actor)));
                } else {
                    out.append("Active JVM args:\n");
                    ManagementFactory.getRuntimeMXBean().getInputArguments().forEach(a -> out.append("  ").append(a).append('\n'));
                    out.append("Pending: ").append(readJvmOptions()).append('\n');
                    out.append("Profiles: ").append(String.join(", ", GC_PROFILES.keySet())).append('\n');
                    out.append("Use: gc run   or   gc profile low-pause\n");
                }
            }
            case "profile" -> {
                String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "status";
                if ("start".equals(sub)) out.append(pretty(runAction("profile-start", null, actor)));
                else if ("stop".equals(sub)) out.append(pretty(runAction("profile-stop", null, actor)));
                else out.append(pretty(profileStatus()));
            }
            case "restart" -> {
                String target = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "api";
                if ("stripe".equals(target) || "payments".equals(target)) {
                    out.append(pretty(runAction("reconnect-stripe", null, actor)));
                } else if ("api".equals(target) || "backend".equals(target)) {
                    out.append(pretty(runAction("restart-api", null, actor)));
                } else {
                    out.append("Restart api or restart stripe");
                }
            }
            default -> out.append("Unknown command. Type help.");
        }
        if (!"logs".equals(cmd) && !"help".equals(cmd) && !"threads".equals(cmd)) {
            audit.record(actor, "OPS_TERMINAL", "Ops", cmd, line);
        }
        return Map.of("output", out.toString().trim(), "cursor", logs.cursor());
    }

    private Map<String, Object> snapshotSummary() {
        Map<String, Object> s = snapshot();
        s.remove("jvmArgs");
        return s;
    }

    private Map<String, Object> jvmStats() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage non = mem.getNonHeapMemoryUsage();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        Map<String, Object> m = new LinkedHashMap<>();
        double processCpu = -1;
        double systemCpu = -1;
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            processCpu = sun.getProcessCpuLoad();
            systemCpu = sun.getCpuLoad();
        }
        m.put("processCpuPct", pct(processCpu));
        m.put("systemCpuPct", pct(systemCpu));
        m.put("availableProcessors", os.getAvailableProcessors());
        m.put("systemLoadAverage", os.getSystemLoadAverage());
        m.put("heapUsedMb", mb(heap.getUsed()));
        m.put("heapMaxMb", mb(heap.getMax()));
        m.put("heapCommittedMb", mb(heap.getCommitted()));
        m.put("heapUsedPct", heap.getMax() > 0 ? round(100.0 * heap.getUsed() / heap.getMax()) : 0);
        m.put("nonHeapUsedMb", mb(non.getUsed()));
        m.put("threads", threads.getThreadCount());
        m.put("daemonThreads", threads.getDaemonThreadCount());
        m.put("peakThreads", threads.getPeakThreadCount());
        List<Map<String, Object>> gcs = new ArrayList<>();
        long gcTime = 0;
        long gcCount = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", gc.getName());
            row.put("count", gc.getCollectionCount());
            row.put("timeMs", gc.getCollectionTime());
            gcs.add(row);
            if (gc.getCollectionTime() > 0) gcTime += gc.getCollectionTime();
            if (gc.getCollectionCount() > 0) gcCount += gc.getCollectionCount();
        }
        m.put("gc", gcs);
        long uptime = Math.max(1, ManagementFactory.getRuntimeMXBean().getUptime());
        m.put("gcOverheadPct", round(100.0 * gcTime / uptime));
        m.put("gcCount", gcCount);
        m.put("gcTimeMs", gcTime);
        return m;
    }

    private Map<String, Object> dbCheck() {
        Map<String, Object> m = new LinkedHashMap<>();
        long t0 = System.nanoTime();
        try (Connection c = dataSource.getConnection()) {
            boolean ok = c.isValid(3);
            m.put("ok", ok);
            m.put("detail", ok ? "Database accepted a connection." : "Database connection is not valid.");
        } catch (Exception e) {
            m.put("ok", false);
            m.put("detail", "Database is not reachable.");
        }
        m.put("latencyMs", round((System.nanoTime() - t0) / 1_000_000.0));
        if (dataSource instanceof HikariDataSource h) {
            var pool = h.getHikariPoolMXBean();
            if (pool != null) {
                m.put("active", pool.getActiveConnections());
                m.put("idle", pool.getIdleConnections());
                m.put("total", pool.getTotalConnections());
                m.put("awaiting", pool.getThreadsAwaitingConnection());
            }
            m.put("maxPool", h.getMaximumPoolSize());
        }
        return m;
    }

    private Map<String, Object> kafkaCheck() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", kafkaEnabled);
        m.put("bootstrap", kafkaBootstrap);
        if (!kafkaEnabled) {
            m.put("ok", true);
            m.put("detail", "Kafka is disabled for this process.");
            return m;
        }
        HostPort hp = firstHostPort(kafkaBootstrap, 9092);
        Probe p = tcp(hp.host, hp.port, 1500);
        m.put("ok", p.ok);
        m.put("latencyMs", p.latencyMs);
        m.put("detail", p.ok ? "Kafka port is open." : "Kafka did not accept a connection.");
        return m;
    }

    private Map<String, Object> networkChecks() {
        Map<String, Object> m = new LinkedHashMap<>();
        HostPort db = jdbcHostPort();
        m.put("postgres", asProbe(tcp(db.host, db.port, 1500)));
        HostPort kafka = firstHostPort(kafkaBootstrap, 9092);
        Map<String, Object> kafkaProbe = asProbe(tcp(kafka.host, kafka.port, 1500));
        kafkaProbe.put("skipped", !kafkaEnabled);
        m.put("kafka", kafkaProbe);
        m.put("stripe", asProbe(tcp("api.stripe.com", 443, 2500)));
        return m;
    }

    private Map<String, Object> ping(String target) {
        return switch (target) {
            case "db", "postgres" -> Map.of("database", dbCheck(), "network", asProbe(tcp(jdbcHostPort().host, jdbcHostPort().port, 1500)));
            case "kafka" -> kafkaCheck();
            case "stripe" -> stripe.probe();
            default -> snapshot();
        };
    }

    private List<Map<String, Object>> findings(Map<String, Object> jvm, Map<String, Object> db,
                                               Map<String, Object> stripeStatus, Map<String, Object> network) {
        List<Map<String, Object>> list = new ArrayList<>();
        double cpu = asDouble(jvm.get("processCpuPct"));
        double heapPct = asDouble(jvm.get("heapUsedPct"));
        double gcOverhead = asDouble(jvm.get("gcOverheadPct"));
        int threads = ((Number) jvm.getOrDefault("threads", 0)).intValue();

        if (cpu >= 85) {
            list.add(finding("critical", "cpu", "Process CPU is " + cpu + "%.",
                    "Capture a thread dump, then restart the API if it does not drop.",
                    List.of("thread-dump", "restart-api")));
        } else if (cpu >= 60) {
            list.add(finding("warn", "cpu", "Process CPU is " + cpu + "%.",
                    "Inspect threads and recent logs for a tight loop.",
                    List.of("thread-dump")));
        } else {
            list.add(finding("ok", "cpu", "CPU is " + cpu + "%.", "No action needed.", List.of()));
        }

        if (heapPct >= 90) {
            list.add(finding("critical", "heap", "Heap is " + heapPct + "% full.",
                    "Run GC, then apply a larger GC profile and restart.",
                    List.of("gc", "apply-gc-profile", "restart-api")));
        } else if (heapPct >= 75) {
            list.add(finding("warn", "heap", "Heap is " + heapPct + "% full.",
                    "Run GC and consider the low-pause or throughput profile.",
                    List.of("gc", "apply-gc-profile")));
        } else {
            list.add(finding("ok", "heap", "Heap is " + heapPct + "% used.", "No action needed.", List.of()));
        }

        if (gcOverhead >= 20) {
            list.add(finding("critical", "gc", "GC is using " + gcOverhead + "% of uptime.",
                    "Apply the low-pause profile (larger heap, shorter pauses) and restart.",
                    List.of("apply-gc-profile", "restart-api")));
        } else if (gcOverhead >= 8) {
            list.add(finding("warn", "gc", "GC is using " + gcOverhead + "% of uptime.",
                    "Run a collection now, or switch GC profile if this stays high.",
                    List.of("gc", "apply-gc-profile")));
        } else {
            list.add(finding("ok", "gc", "GC overhead is " + gcOverhead + "%.", "No action needed.", List.of()));
        }

        if (threads >= 200) {
            list.add(finding("warn", "threads", "Thread count is " + threads + ".",
                    "Dump threads to look for blocked or leaking workers.", List.of("thread-dump")));
        }

        if (!Boolean.TRUE.equals(db.get("ok"))) {
            list.add(finding("critical", "database", String.valueOf(db.get("detail")),
                    "Retry the network check, then restart the API to rebuild the pool.",
                    List.of("retry-network", "restart-api")));
        }

        Object stripeOk = stripeStatus.get("reachable");
        boolean mock = Boolean.TRUE.equals(stripeStatus.get("mock"));
        if (mock) {
            list.add(finding("warn", "stripe", "Stripe is in mock mode.",
                    "Set live keys if you need real payments. Reconnect after changing env and restart.",
                    List.of("reconnect-stripe", "restart-api")));
        } else if (!Boolean.TRUE.equals(stripeOk)) {
            list.add(finding("critical", "stripe", String.valueOf(stripeStatus.get("detail")),
                    "Reconnect Stripe, check outbound 443, then restart the API if it stays down.",
                    List.of("reconnect-stripe", "retry-network", "restart-api")));
        } else if (!Boolean.TRUE.equals(stripeStatus.get("webhookConfigured"))) {
            list.add(finding("warn", "stripe", "Webhook secret is not set.",
                    "Payments can still work from the browser confirm path. Add STRIPE_WEBHOOK_SECRET for async confirms.",
                    List.of("reconnect-stripe")));
        } else {
            list.add(finding("ok", "stripe", "Stripe API is reachable.", "No action needed.", List.of()));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> stripeNet = (Map<String, Object>) network.get("stripe");
        if (stripeNet != null && !Boolean.TRUE.equals(stripeNet.get("ok"))) {
            list.add(finding("warn", "network", "Cannot open TLS to api.stripe.com:443.",
                    "Check DNS and outbound HTTPS, then retry.", List.of("retry-network")));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> pgNet = (Map<String, Object>) network.get("postgres");
        if (pgNet != null && !Boolean.TRUE.equals(pgNet.get("ok"))) {
            list.add(finding("critical", "network", "Postgres port is closed.",
                    "Retry, then restart the API after the database is up.",
                    List.of("retry-network", "restart-api")));
        }

        Recording rec = recording.get();
        if (rec != null) {
            list.add(finding("ok", "profiling", "A JFR recording is in progress.",
                    "Stop it from this page when you have enough samples.", List.of("profile-stop")));
        }
        return list;
    }

    private Map<String, Object> finding(String severity, String area, String summary, String advice, List<String> actions) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("severity", severity);
        m.put("area", area);
        m.put("summary", summary);
        m.put("advice", advice);
        m.put("actions", actions);
        return m;
    }

    private Map<String, Object> forceGc() {
        long before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        System.gc();
        long after = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("heapBeforeMb", mb(before));
        m.put("heapAfterMb", mb(after));
        m.put("output", "Requested GC. Heap " + mb(before) + " MB → " + mb(after) + " MB.");
        return m;
    }

    private String threadDump() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = bean.dumpAllThreads(true, true);
        StringBuilder sb = new StringBuilder();
        sb.append("Threads: ").append(infos.length).append('\n');
        for (ThreadInfo info : infos) {
            sb.append(info.toString());
        }
        return sb.toString();
    }

    private Map<String, Object> startProfile() {
        if (recording.get() != null) {
            return Map.of("ok", true, "output", "A recording is already running.", "profiling", profileStatus());
        }
        try {
            Recording rec = new Recording(Configuration.getConfiguration("profile"));
            rec.setName("ops-" + Instant.now().toEpochMilli());
            rec.setMaxAge(Duration.ofMinutes(10));
            rec.setToDisk(true);
            Path dest = opsDir().resolve("profile-" + Instant.now().toEpochMilli() + ".jfr");
            rec.setDestination(dest);
            rec.start();
            recording.set(rec);
            return Map.of("ok", true, "output", "Started JFR recording → " + dest, "profiling", profileStatus());
        } catch (Exception e) {
            throw new IllegalStateException("Profiling could not be started on this JVM.");
        }
    }

    private Map<String, Object> stopProfile() {
        Recording rec = recording.getAndSet(null);
        if (rec == null) {
            return Map.of("ok", true, "output", "No recording was running.");
        }
        try {
            rec.stop();
            rec.close();
            String path = rec.getDestination() == null ? "" : rec.getDestination().toString();
            return Map.of("ok", true, "output", "Recording saved" + (path.isBlank() ? "." : " to " + path));
        } catch (Exception e) {
            throw new IllegalStateException("The recording could not be saved.");
        }
    }

    private Map<String, Object> profileStatus() {
        Recording rec = recording.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", rec != null);
        if (rec != null) {
            m.put("name", rec.getName());
            m.put("state", rec.getState() == null ? "" : rec.getState().toString());
            m.put("destination", rec.getDestination() == null ? "" : rec.getDestination().toString());
        }
        return m;
    }

    private Map<String, Object> reconnectStripe() {
        Map<String, Object> probe = stripe.probe();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", Boolean.TRUE.equals(probe.get("reachable")) || Boolean.TRUE.equals(probe.get("mock")));
        m.put("stripe", probe);
        m.put("output", probe.get("detail"));
        return m;
    }

    private Map<String, Object> applyGcProfile(String profile) {
        String key = profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT);
        if ("lowpause".equals(key) || "low_pause".equals(key)) key = "low-pause";
        String flags = GC_PROFILES.get(key);
        if (flags == null) {
            throw new IllegalArgumentException("Choose balanced, low-pause, throughput, or constrained.");
        }
        try {
            Path file = opsDir().resolve("jvm.options");
            Files.writeString(file, flags + System.lineSeparator());
        } catch (IOException e) {
            throw new IllegalStateException("Could not save JVM options.");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("profile", key);
        m.put("flags", flags);
        m.put("output", "Saved " + key + " flags. Restart the API for them to take effect.");
        m.put("needsRestart", true);
        return m;
    }

    private Map<String, Object> scheduleRestart(String actor) {
        log.warn("Ops restart requested by {}", actor);
        restartPool.schedule(() -> {
            try {
                int code = SpringApplication.exit(ctx, () -> 0);
                System.exit(code);
            } catch (Exception e) {
                System.exit(0);
            }
        }, 1500, TimeUnit.MILLISECONDS);
        return Map.of("ok", true, "output", "API will exit in about 2 seconds so the process manager can start it again.",
                "restarting", true);
    }

    private String readJvmOptions() {
        Path file = Path.of(uploadDir).toAbsolutePath().normalize().resolve("ops").resolve("jvm.options");
        try {
            if (Files.exists(file)) {
                return Files.readString(file).trim();
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    private Path opsDir() throws IOException {
        Path dir = Path.of(uploadDir).toAbsolutePath().normalize().resolve("ops");
        Files.createDirectories(dir);
        return dir;
    }

    private HostPort jdbcHostPort() {
        try {
            String url = jdbcUrl == null ? "" : jdbcUrl;
            if (url.startsWith("jdbc:")) url = url.substring(5);
            URI uri = URI.create(url);
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String host = uri.getHost() == null ? "localhost" : uri.getHost();
            return new HostPort(host, port);
        } catch (Exception e) {
            return new HostPort("localhost", 5432);
        }
    }

    private HostPort firstHostPort(String bootstrap, int fallbackPort) {
        if (bootstrap == null || bootstrap.isBlank()) {
            return new HostPort("localhost", fallbackPort);
        }
        String first = bootstrap.split(",")[0].trim();
        int idx = first.lastIndexOf(':');
        if (idx <= 0) return new HostPort(first, fallbackPort);
        try {
            return new HostPort(first.substring(0, idx), Integer.parseInt(first.substring(idx + 1)));
        } catch (NumberFormatException e) {
            return new HostPort(first, fallbackPort);
        }
    }

    private Probe tcp(String host, int port, int timeoutMs) {
        long t0 = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return new Probe(true, round((System.nanoTime() - t0) / 1_000_000.0), host + ":" + port + " open");
        } catch (Exception e) {
            return new Probe(false, round((System.nanoTime() - t0) / 1_000_000.0), host + ":" + port + " closed");
        }
    }

    private Map<String, Object> asProbe(Probe p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", p.ok);
        m.put("latencyMs", p.latencyMs);
        m.put("detail", p.detail);
        return m;
    }

    private static String formatLog(LogLine l) {
        return l.at() + " " + l.level() + " " + l.logger() + " " + l.message();
    }

    private String pretty(Object o) {
        if (o == null) return "";
        if (o instanceof String s) return s;
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static double pct(double load) {
        if (load < 0) return 0;
        return round(load * 100.0);
    }

    private static long mb(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0));
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return 0;
    }

    private record HostPort(String host, int port) {}
    private record Probe(boolean ok, double latencyMs, String detail) {}
}
