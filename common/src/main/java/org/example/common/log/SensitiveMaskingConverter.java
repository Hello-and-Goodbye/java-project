package org.example.common.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * logback 消息转换器：在写出前对日志正文做脱敏。
 * <p>
 * 在 logback 配置中以 {@code <conversionRule>} 注册后，pattern 里用 %maskedMsg 替代 %msg，
 * 即可对所有 appender 统一生效，业务代码无需改动。
 * <p>
 * 这是兜底手段：脱敏发生在格式化阶段，对已经进入 message 的敏感值做替换。
 * 首选做法仍是不把敏感对象交给 logger。
 */
public class SensitiveMaskingConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveMasker.mask(super.convert(event));
    }
}
