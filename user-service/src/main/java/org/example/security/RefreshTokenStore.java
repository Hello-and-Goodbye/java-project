package org.example.security;

import org.example.common.util.RedisUtil;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * refresh token 的 Redis 存储与生命周期管理。
 * <p>
 * key 设计：{@code refresh:{username}:{jti}} → refresh token 字符串。
 * 每个设备一个 jti，因此多设备登录各自独立、互不覆盖。
 * <p>
 * 滑动续期：每次刷新写入新 jti 并删除旧 jti，TTL 重置为 7 天，但不超过“首次登录 + 30 天”的绝对上限。
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final RedisUtil redisUtil;

    public RefreshTokenStore(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    private String key(String username, String jti) {
        return KEY_PREFIX + username + ":" + jti;
    }

    /**
     * 存储 refresh token。
     * @param ttlMillis 本次的 TTL(毫秒)，调用方已按“不超过绝对上限”算好
     */
    public void store(String username, String jti, String token, long ttlMillis) {
        redisUtil.set(key(username, jti), token, ttlMillis, TimeUnit.MILLISECONDS);
    }

    /** 校验某设备的 refresh token 是否仍有效(存在且与传入值一致) */
    public boolean isValid(String username, String jti, String token) {
        Object stored = redisUtil.get(key(username, jti));
        return stored != null && stored.equals(token);
    }

    /** 吊销单个设备(登出 / 刷新时删旧) */
    public void revoke(String username, String jti) {
        redisUtil.delete(key(username, jti));
    }

    /** 吊销某用户全部设备(改密 / 强制全端下线) */
    public long revokeAll(String username) {
        return redisUtil.deleteByPrefix(KEY_PREFIX + username + ":");
    }
}
