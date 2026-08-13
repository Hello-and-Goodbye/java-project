package org.example.common.log;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志体系可配置项，前缀 {@code app.log}。
 */
@ConfigurationProperties(prefix = "app.log")
public class LogProperties {

    /** 是否开启访问日志 */
    private boolean accessLogEnabled = true;

    /** 慢请求阈值(毫秒)，超过则访问日志升级为 WARN */
    private long slowRequestThresholdMs = 1000L;

    public boolean isAccessLogEnabled() {
        return accessLogEnabled;
    }

    public void setAccessLogEnabled(boolean accessLogEnabled) {
        this.accessLogEnabled = accessLogEnabled;
    }

    public long getSlowRequestThresholdMs() {
        return slowRequestThresholdMs;
    }

    public void setSlowRequestThresholdMs(long slowRequestThresholdMs) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }
}
