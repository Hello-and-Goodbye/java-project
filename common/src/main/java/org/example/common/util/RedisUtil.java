package org.example.common.util;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类：对 {@link RedisTemplate} 的常用命令做二次封装，屏蔽 opsForXxx 细节，
 * 统一异常与空值处理，方便业务层调用。
 * <p>
 * 依赖 common 中定义的 {@code RedisTemplate<String, Object>}；用 {@link ConditionalOnClass} 守卫，
 * 仅当类路径存在 Redis 相关类(即服务引入了 Redis)时才注册，避免 gateway 等无 Redis 的服务报错。
 */
@Component
@ConditionalOnClass(RedisTemplate.class)
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== 通用 key 操作 ====================

    /** 设置过期时间(秒) */
    public boolean expire(String key, long seconds) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, seconds, TimeUnit.SECONDS));
    }

    /** 设置过期时间(自定义单位) */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    /** 获取剩余过期时间(秒)：-1 永不过期，-2 不存在 */
    public long getExpire(String key) {
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire == null ? -2 : expire;
    }

    /** 判断 key 是否存在 */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /** 删除单个 key */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /** 批量删除 key，返回删除数量 */
    public long delete(Collection<String> keys) {
        Long count = redisTemplate.delete(keys);
        return count == null ? 0 : count;
    }

    // ==================== String ====================

    /** 存值(永久) */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** 存值并设置过期时间(秒)；timeout<=0 表示永久 */
    public void set(String key, Object value, long timeoutSeconds) {
        if (timeoutSeconds > 0) {
            redisTemplate.opsForValue().set(key, value, timeoutSeconds, TimeUnit.SECONDS);
        } else {
            set(key, value);
        }
    }

    /** 存值并设置过期时间(自定义单位) */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 取值 */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    /**
     * 仅当 key 不存在时才设置(SET NX)，常用于分布式锁 / 防重复提交。
     * @return true 表示设置成功(抢到)，false 表示 key 已存在
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    /** 递增，返回递增后的值 */
    public long increment(String key, long delta) {
        Long v = redisTemplate.opsForValue().increment(key, delta);
        return v == null ? 0 : v;
    }

    /** 递减，返回递减后的值 */
    public long decrement(String key, long delta) {
        Long v = redisTemplate.opsForValue().decrement(key, delta);
        return v == null ? 0 : v;
    }

    // ==================== Hash ====================

    /** 获取 hash 中某个字段 */
    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /** 获取整个 hash */
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /** 设置 hash 字段 */
    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /** 设置 hash 字段并给整个 key 设过期时间(秒) */
    public void hSet(String key, String field, Object value, long timeoutSeconds) {
        redisTemplate.opsForHash().put(key, field, value);
        if (timeoutSeconds > 0) {
            expire(key, timeoutSeconds);
        }
    }

    /** 批量设置 hash */
    public void hSetAll(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    /** 删除 hash 中一个或多个字段 */
    public long hDelete(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    /** 判断 hash 是否存在某字段 */
    public boolean hHasKey(String key, String field) {
        return redisTemplate.opsForHash().hasKey(key, field);
    }

    /** hash 字段递增 */
    public long hIncrement(String key, String field, long delta) {
        return redisTemplate.opsForHash().increment(key, field, delta);
    }

    // ==================== List ====================

    /** 取 list 指定范围元素；start=0,end=-1 表示全部 */
    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /** list 长度 */
    public long lSize(String key) {
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0 : size;
    }

    /** 右侧入队(尾部追加) */
    public long lPush(String key, Object value) {
        Long len = redisTemplate.opsForList().rightPush(key, value);
        return len == null ? 0 : len;
    }

    /** 右侧批量入队 */
    public long lPushAll(String key, Collection<Object> values) {
        Long len = redisTemplate.opsForList().rightPushAll(key, values.toArray());
        return len == null ? 0 : len;
    }

    /** 左侧出队(头部弹出) */
    public Object lPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    // ==================== Set ====================

    /** 获取 set 全部成员 */
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /** 判断是否为 set 成员 */
    public boolean sIsMember(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    /** 添加 set 成员，返回新增数量 */
    public long sAdd(String key, Object... values) {
        Long count = redisTemplate.opsForSet().add(key, values);
        return count == null ? 0 : count;
    }

    /** set 成员数量 */
    public long sSize(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0 : size;
    }

    /** 移除 set 成员，返回移除数量 */
    public long sRemove(String key, Object... values) {
        Long count = redisTemplate.opsForSet().remove(key, values);
        return count == null ? 0 : count;
    }

    // ==================== ZSet ====================

    /** 添加 zset 成员及分数 */
    public boolean zAdd(String key, Object value, double score) {
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, value, score));
    }

    /** 按分数区间取成员(升序) */
    public Set<Object> zRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max);
    }

    /** 移除 zset 成员 */
    public long zRemove(String key, Object... values) {
        Long count = redisTemplate.opsForZSet().remove(key, values);
        return count == null ? 0 : count;
    }

    // ==================== 便捷方法 ====================

    /** 批量删除某前缀的所有 key(慎用：keys 命令在大库上有性能风险，建议仅用于开发/小数据量场景) */
    public long deleteByPrefix(String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (CollectionUtils.isEmpty(keys)) {
            return 0;
        }
        return delete(keys);
    }
}
