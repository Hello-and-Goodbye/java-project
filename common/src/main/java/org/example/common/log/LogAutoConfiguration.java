package org.example.common.log;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 日志体系自动配置。
 * <p>
 * 只在 <b>Servlet</b> 型 Web 应用生效({@code type = SERVLET})：
 * gateway 基于 WebFlux，其线程模型下 ThreadLocal(MDC) 会随线程切换丢失，
 * 需用响应式专用方案(见 gateway 模块的 TraceWebFilter)，不能复用这里的 Servlet 过滤器。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Filter.class)
@EnableConfigurationProperties(LogProperties.class)
public class LogAutoConfiguration {

    /**
     * traceId 过滤器：用 FilterRegistrationBean 显式声明顺序，
     * 比依赖 @Order 的隐式解析更可控。
     * <p>
     * 必须排在 Spring Security 的 FilterChainProxy(默认 order = -100)之前，
     * 否则鉴权失败(401/403)的日志会缺少 traceId——而这类日志恰恰最需要追踪。
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("traceIdFilter");
        return registration;
    }

    /** 访问日志：紧随 traceId 之后，保证日志已带链路信息 */
    @Bean
    @ConditionalOnProperty(prefix = "app.log", name = "access-log-enabled",
            matchIfMissing = true)
    public FilterRegistrationBean<AccessLogFilter> accessLogFilterRegistration(
            LogProperties properties) {
        FilterRegistrationBean<AccessLogFilter> registration = new FilterRegistrationBean<>(
                new AccessLogFilter(properties.getSlowRequestThresholdMs()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        registration.setName("accessLogFilter");
        return registration;
    }
}
