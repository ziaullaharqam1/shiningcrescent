package com.shiningcrescent.ops;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MemoryLogBuffer extends AppenderBase<ILoggingEvent> {
    private static final int MAX = 2500;
    private final ConcurrentLinkedDeque<LogLine> lines = new ConcurrentLinkedDeque<>();
    private final AtomicLong seq = new AtomicLong();

    public record LogLine(long id, String at, String level, String logger, String message) {}

    @PostConstruct
    public void attach() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(ctx);
        setName("ops-memory");
        start();
        Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(this);
    }

    @Override
    protected void append(ILoggingEvent event) {
        LogLine line = new LogLine(
                seq.incrementAndGet(),
                Instant.ofEpochMilli(event.getTimeStamp()).toString(),
                event.getLevel() == null ? "INFO" : event.getLevel().toString(),
                event.getLoggerName(),
                event.getFormattedMessage());
        lines.addLast(line);
        while (lines.size() > MAX) {
            lines.pollFirst();
        }
    }

    public long cursor() {
        return seq.get();
    }

    public List<LogLine> recent(int limit, String query, Long afterId) {
        int cap = Math.max(1, Math.min(limit, MAX));
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<LogLine> all = new ArrayList<>(lines);
        List<LogLine> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && out.size() < cap; i--) {
            LogLine line = all.get(i);
            if (afterId != null && line.id() <= afterId) {
                continue;
            }
            if (!q.isEmpty()) {
                String hay = (line.level() + " " + line.logger() + " " + line.message()).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) {
                    continue;
                }
            }
            out.add(line);
        }
        java.util.Collections.reverse(out);
        return out;
    }
}
