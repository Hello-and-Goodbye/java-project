package org.example.common.log;

import java.util.regex.Pattern;

/**
 * 日志脱敏：把敏感字段值替换为掩码，避免密码/token/证件号落盘。
 * <p>
 * 定位是“兜底”而非“唯一防线”——正确做法仍是不把敏感对象传给 logger。
 * 这里覆盖两类常见泄漏：JSON 请求体打印、以及 key=value 形式的拼接日志。
 * <p>
 * 出于性能考虑只做正则替换，不解析 JSON；正则均预编译且限定在敏感 key 附近，
 * 避免对每条日志做全文扫描。
 */
public final class SensitiveMasker {

    private static final String MASK = "***";

    /** 需要完全遮蔽的字段名(不区分大小写)，值一律替换为 *** */
    private static final Pattern JSON_SENSITIVE = Pattern.compile(
            "(\"(?:password|passwd|pwd|oldPassword|newPassword|confirmPassword|"
                    + "accessToken|refreshToken|token|secret|apiKey|appSecret|"
                    + "privateKey|credential|authorization)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    /** key=value / key: value 形式(日志拼接常见) */
    private static final Pattern KV_SENSITIVE = Pattern.compile(
            "\\b(password|passwd|pwd|accessToken|refreshToken|token|secret|apiKey|"
                    + "appSecret|privateKey|credential)\\b\\s*[=:]\\s*([^,;\\s&)}\\]]+)",
            Pattern.CASE_INSENSITIVE);

    /** Bearer token */
    private static final Pattern BEARER = Pattern.compile(
            "(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*", Pattern.CASE_INSENSITIVE);

    /** 手机号：保留前 3 后 4 */
    private static final Pattern MOBILE = Pattern.compile("\\b(1[3-9]\\d)\\d{4}(\\d{4})\\b");

    /** 身份证：保留前 6 后 4 */
    private static final Pattern ID_CARD = Pattern.compile("\\b(\\d{6})\\d{8}(\\d{3}[0-9Xx])\\b");

    /** 银行卡：保留后 4 */
    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{12,15}(\\d{4})\\b");

    /** 邮箱：保留首字符与域名 */
    private static final Pattern EMAIL = Pattern.compile(
            "\\b([A-Za-z0-9])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b");

    private SensitiveMasker() {
    }

    /**
     * 对整条日志文本脱敏。
     *
     * @param message 原始日志，允许为 null
     * @return 脱敏后的文本；入参为 null 时返回 null
     */
    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String result = message;
        result = JSON_SENSITIVE.matcher(result).replaceAll("$1\"" + MASK + "\"");
        result = KV_SENSITIVE.matcher(result).replaceAll("$1=" + MASK);
        result = BEARER.matcher(result).replaceAll("$1" + MASK);
        result = MOBILE.matcher(result).replaceAll("$1****$2");
        result = ID_CARD.matcher(result).replaceAll("$1********$2");
        result = BANK_CARD.matcher(result).replaceAll("************$1");
        result = EMAIL.matcher(result).replaceAll("$1***$2");
        return result;
    }

    /** 手机号单独脱敏，供业务代码显式调用 */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return MOBILE.matcher(mobile).replaceAll("$1****$2");
    }

    /** token 只保留头尾各 4 位，便于排查时比对是否同一个 token */
    public static String maskToken(String token) {
        if (token == null || token.length() <= 12) {
            return MASK;
        }
        return token.substring(0, 4) + MASK + token.substring(token.length() - 4);
    }

    /** 通用：保留前 keepPrefix 后 keepSuffix 位 */
    public static String maskPartial(String value, int keepPrefix, int keepSuffix) {
        if (value == null || value.length() <= keepPrefix + keepSuffix) {
            return MASK;
        }
        return value.substring(0, keepPrefix) + MASK
                + value.substring(value.length() - keepSuffix);
    }
}
