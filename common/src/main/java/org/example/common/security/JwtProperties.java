package org.example.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 相关配置项，绑定 app.jwt.* 。
 * 网关与各业务服务必须配置<b>相同的 secret</b>，否则彼此签发的 token 无法互验。
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HMAC-SHA256 密钥(Base64)，>= 256bit。生产环境请放到环境变量/配置中心。 */
    private String secret;

    /** access token 有效期(毫秒)，默认 15 分钟 */
    private long accessExpiration = 900_000L;

    /** refresh token 有效期(毫秒)，默认 7 天 */
    private long refreshExpiration = 604_800_000L;

    /** refresh token 绝对上限(毫秒)：从首次登录起最长可滑动多久，默认 30 天 */
    private long refreshMaxLifetime = 2_592_000_000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessExpiration() {
        return accessExpiration;
    }

    public void setAccessExpiration(long accessExpiration) {
        this.accessExpiration = accessExpiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public void setRefreshExpiration(long refreshExpiration) {
        this.refreshExpiration = refreshExpiration;
    }

    public long getRefreshMaxLifetime() {
        return refreshMaxLifetime;
    }

    public void setRefreshMaxLifetime(long refreshMaxLifetime) {
        this.refreshMaxLifetime = refreshMaxLifetime;
    }
}
