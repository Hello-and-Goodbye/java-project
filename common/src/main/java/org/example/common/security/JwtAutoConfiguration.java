package org.example.common.security;

import io.jsonwebtoken.Jwts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自动装配 {@link JwtService}，供网关与各业务服务共用。
 * <p>
 * 用自动配置(而非 @Service + 组件扫描)的原因：gateway 只扫描 org.example.gateway 包，
 * 扫不到 common 里的类；通过 spring.factories/AutoConfiguration.imports 注册则不受扫描包限制。
 */
@Configuration
@ConditionalOnClass(Jwts.class)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtProperties props) {
        return new JwtService(props.getSecret(), props.getAccessExpiration(), props.getRefreshExpiration());
    }
}
