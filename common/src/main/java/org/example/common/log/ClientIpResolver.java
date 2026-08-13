package org.example.common.log;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 解析真实客户端 IP。
 * <p>
 * 服务位于网关/Nginx 之后时，{@code getRemoteAddr()} 拿到的是代理 IP，
 * 需从转发头中取。取 X-Forwarded-For 的<b>第一个</b>值：该头是逗号分隔的追加链，
 * 格式为 {@code client, proxy1, proxy2}。
 * <p>
 * 注意：转发头可被客户端伪造，因此仅用于日志排查与粗粒度统计，
 * <b>不可作为安全判定依据</b>(如 IP 白名单、限流的唯一凭据)。
 * 生产环境应在最外层网关强制覆写该头。
 */
public final class ClientIpResolver {

    private static final String UNKNOWN = "unknown";

    /** 按优先级依次尝试的代理头 */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (isValid(value)) {
                // X-Forwarded-For 可能是 "client, proxy1, proxy2"，第一个才是客户端
                int comma = value.indexOf(',');
                return comma > 0 ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isValid(String value) {
        return StringUtils.hasText(value) && !UNKNOWN.equalsIgnoreCase(value.trim());
    }
}
