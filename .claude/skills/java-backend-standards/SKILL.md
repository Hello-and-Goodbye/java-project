---
name: java-backend-standards
description: Java 后端工程规范（编码 / 接口 / 安全三部分），基于阿里巴巴 Java 开发手册与主流大厂 Code Review 标准。在编写或评审 Java 后端代码时使用——新增 Controller/Service、设计接口契约、处理异常与日志、实现认证鉴权、排查并发或 SQL 问题时都应先读本规范。
---

# Java 后端工程规范

分三部分：**编码规范**（怎么写）、**接口规范**（对外契约）、**安全规范**（认证鉴权与防护）。

规约分级：
- **【强制】** 必须遵守，违反视为缺陷
- **【推荐】** 应当遵守，偏离需说明理由
- **【参考】** 视场景取舍

---

## 一、编码规范

### 1.1 命名

**【强制】** 类名 UpperCamelCase；方法名、变量名 lowerCamelCase；常量全大写下划线分隔。

**【强制】** 禁止拼音与英文混用，禁止无意义缩写。

```java
// ✗ 反例
int a;  String userNaem;  void getXxx();  int DEFAULT_num;

// ✓ 正例
int retryCount;  String userName;  void getUserById(Long id);
static final int DEFAULT_PAGE_SIZE = 20;
```

**【强制】** 布尔类型字段不加 `is` 前缀。POJO 里 `isDeleted` 会导致部分序列化框架解析出属性名 `deleted`，引发不一致。

**【推荐】** 命名遵循领域约定：

| 层 | 后缀 | 职责 |
|---|---|---|
| Controller | `XxxController` | 参数校验、协议转换，不写业务 |
| Service 接口 | `XxxService` | 业务编排 |
| Service 实现 | `XxxServiceImpl` | |
| 数据访问 | `XxxRepository` / `XxxMapper` | 只做持久化 |
| 入参 | `XxxRequest` / `XxxCmd` / `XxxQuery` | |
| 出参 | `XxxResponse` / `XxxVO` / `XxxDTO` | |
| 持久化实体 | `XxxEntity` 或裸领域名 | |

**【强制】** 各层对象不得跨层复用。Entity 直接返回给前端会泄漏字段（如密码哈希、内部状态），也让数据库结构与 API 契约耦合。

### 1.2 分层与依赖方向

**【强制】** 依赖单向：`Controller → Service → Repository`。禁止 Service 反向依赖 Controller，禁止 Repository 调 Service。

**【强制】** Controller 不得包含业务逻辑。判断标准：Controller 里出现 `if` 业务分支、循环处理数据、多次 Repository 调用，都应下沉到 Service。

**【推荐】** Service 方法保持单一职责。一个方法超过 80 行或嵌套超过 3 层，考虑拆分。

### 1.3 异常处理

**【强制】** 禁止捕获后吞掉异常。

```java
// ✗ 反例：异常消失，问题无法定位
try {
    doSomething();
} catch (Exception e) {
    // ignore
}

// ✗ 反例：丢失堆栈
catch (Exception e) {
    log.error("失败：" + e.getMessage());
}

// ✓ 正例：保留堆栈，附带上下文
catch (SomeSpecificException e) {
    log.error("处理订单失败, orderId={}", orderId, e);
    throw new BizException(ErrorCode.ORDER_PROCESS_FAILED, e);
}
```

**【强制】** 禁止用异常做流程控制。异常构造要抓取堆栈，开销远高于条件判断。

**【强制】** 捕获具体异常类型，不要一律 `catch (Exception e)`。宽泛捕获会掩盖 `NullPointerException` 这类代码缺陷。

**【强制】** 业务异常与系统异常分离：

- **业务异常**（余额不足、用户已存在）→ 自定义受检/非受检异常，携带错误码，返回 4xx
- **系统异常**（DB 连不上、NPE）→ 不在业务代码中捕获，交由全局处理器兜底，返回 5xx，且**不得向客户端暴露堆栈或 SQL**

**【强制】** 统一异常处理落在 `@RestControllerAdvice`，业务代码不做重复的 try-catch 包装。

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        log.error("未处理的服务器异常", e);   // 细节进日志
        return Result.error(500, "服务器内部错误");  // 客户端只看到通用文案
    }
}
```

**注意异常处理器的顺序与覆盖范围**：Spring Security 的认证/授权失败发生在进入 Controller **之前**，`@RestControllerAdvice` 捕获不到，必须单独实现 `AuthenticationEntryPoint` 与 `AccessDeniedHandler`（见 3.4）。

### 1.4 空值处理

**【强制】** 返回集合时返回空集合而非 `null`。

```java
// ✗ 调用方必须判空，漏判即 NPE
return list.isEmpty() ? null : list;

// ✓
return Collections.emptyList();
```

**【推荐】** 可能不存在的单个对象用 `Optional` 作为返回类型，但不要用作字段或方法参数。

**【强制】** 跨层接收的对象一律视为可能为 null，尤其是 RPC 返回值、缓存读取、`Map.get()`。

### 1.5 并发

**【强制】** 线程池必须手动创建，禁止 `Executors.newFixedThreadPool()` / `newCachedThreadPool()`。前者队列无界会 OOM，后者线程数无界会耗尽资源。

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1000),              // 有界队列
        new ThreadFactoryBuilder().setNameFormat("order-pool-%d").build(),  // 可辨识的线程名
        new ThreadPoolExecutor.CallerRunsPolicy());  // 明确的拒绝策略
```

**【强制】** 线程名必须有业务含义。默认的 `pool-1-thread-3` 让线上 dump 无法定位。

**【强制】** `ThreadLocal` 必须在 `finally` 中 `remove()`。线程池复用线程，残留值会串到下一个请求——这是典型的用户数据串号事故。

```java
try {
    UserContext.set(currentUser);
    chain.doFilter(req, resp);
} finally {
    UserContext.clear();   // 必须
}
```

**【强制】** 加锁顺序全局一致，避免死锁。锁的粒度尽量小，不要在锁内做 IO 或远程调用。

**【推荐】** 优先用 `ConcurrentHashMap`、`AtomicLong` 等并发容器，而非 `synchronized` 包裹普通容器。

### 1.6 数据库与 SQL

**【强制】** 禁止 SQL 字符串拼接用户输入，一律参数化。

```java
// ✗ SQL 注入
"SELECT * FROM users WHERE name = '" + name + "'"

// ✓ 参数绑定
jdbcTemplate.query("SELECT * FROM users WHERE name = ?", rowMapper, name);
```

**【强制】** 禁止 `SELECT *`。字段变更会打破映射，也传输了无用列。

**【强制】** 禁止在循环中查库（N+1）。用 `IN` 批量查询或 JOIN 一次取回。

```java
// ✗ N+1：100 个订单 = 101 次查询
for (Order o : orders) {
    User u = userRepository.findById(o.getUserId());
}

// ✓ 一次批量
Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
```

**【强制】** 事务范围只包住需要原子性的 DB 操作。不要把 HTTP 调用、消息发送、文件 IO 放进事务——长事务会持有连接和行锁，拖垮连接池。

**【强制】** `@Transactional` 的失效场景要清楚：同类内部方法自调用不走代理、方法非 public、异常被吞、默认只对 `RuntimeException` 回滚。需要对受检异常回滚时显式声明 `rollbackFor`。

**【推荐】** 任何列表查询都要分页，且限制单页最大条数。

### 1.7 日志

**【强制】** 用参数占位符，不要字符串拼接。拼接在日志级别未启用时仍会执行。

```java
// ✗
log.debug("user info: " + user.toString());
// ✓
log.debug("user info: {}", user);
```

**【强制】** 日志中禁止输出敏感信息：密码、token、身份证、手机号、银行卡、密钥。必要时脱敏。

**【强制】** 异常日志必须传入异常对象（最后一个参数，不带占位符），否则丢失堆栈。

**【推荐】** 日志级别语义：

| 级别 | 用途 |
|---|---|
| ERROR | 需要人工介入的故障 |
| WARN | 降级、重试、非预期但可继续 |
| INFO | 关键业务节点（下单、支付、登录） |
| DEBUG | 排查细节，生产默认关闭 |

**【推荐】** 关键链路带 traceId，便于串联分布式调用。

### 1.8 注释

**【推荐】** 注释解释**为什么**，而非**做什么**。代码本身已经说明做什么。

```java
// ✗ 冗余
// 将 count 加 1
count++;

// ✓ 说明意图
// 上游偶发重复推送，用 jti 去重而非依赖唯一索引，避免主键冲突打日志
```

**【强制】** 修改代码时同步更新注释。过期注释比没有注释更有害。

---

## 二、接口规范

### 2.1 统一响应结构

**【强制】** 所有 REST 接口返回同一外层结构，不因场景变化。

```java
public class Result<T> {
    private int code;        // 业务状态码
    private String message;  // 提示信息
    private T data;          // 业务数据，无数据时为 null
}
```

**【强制】** HTTP 状态码与业务码各司其职：

- HTTP 状态码表达**协议层**语义：200 成功、400 参数错、401 未认证、403 无权限、404 不存在、500 服务端错误
- 业务码表达**业务层**语义：如 `40101` token 过期、`40102` token 无效

不要一律返回 HTTP 200 再用 body 里的 code 区分错误——这会让网关、监控、重试策略、浏览器缓存全部失效。

**【强制】** 错误响应不得向客户端暴露堆栈、SQL、内部类名、文件路径。这些信息进日志。

### 2.2 URL 与方法

**【强制】** 路径用名词复数表示资源，动作由 HTTP 方法表达。

```
✓ GET    /api/users          列表
✓ GET    /api/users/{id}     详情
✓ POST   /api/users          创建
✓ PUT    /api/users/{id}     全量更新
✓ PATCH  /api/users/{id}     部分更新
✓ DELETE /api/users/{id}     删除

✗ POST /api/getUserList
✗ GET  /api/deleteUser?id=1     ← GET 不得有副作用
```

**【强制】** GET 必须无副作用且幂等。GET 会被浏览器预取、CDN 缓存、爬虫触发。

**【强制】** 路径小写，多单词用连字符：`/api/order-items` 而非 `/api/orderItems`。

**【推荐】** 版本化：`/api/v1/users`。破坏性变更升版本，不在原版本上改语义。

### 2.3 参数校验

**【强制】** 所有外部输入都要校验，包括 body、query、path、header。不信任任何客户端数据。

**【强制】** 用声明式校验，不要在方法体里手写一堆 if。

```java
public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 32, message = "用户名长度需在 4-32 之间")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只允许字母数字下划线")
        String username,

        @NotBlank @Size(min = 8, max = 64)
        String password,

        @Email @NotBlank
        String email
) {}

@PostMapping("/users")
public Result<UserResponse> create(@Valid @RequestBody CreateUserRequest req) { ... }
```

**【强制】** 校验失败统一转成 400 + 可读错误信息，由全局处理器完成（见 1.3）。

**【强制】** 分页参数必须有上限。`pageSize=999999` 会打穿数据库。

### 2.4 幂等性

**【强制】** 写接口（POST/PUT/DELETE）需考虑重复提交。网络重试、用户双击、消息重投都会导致重复请求。

常见方案：

| 方案 | 适用场景 |
|---|---|
| 唯一索引 | 天然去重的业务键（订单号、手机号） |
| 客户端幂等键 | 请求头带 `Idempotency-Key`，服务端存 Redis 去重 |
| 状态机 | 只允许特定状态流转，重复请求被状态判断拦下 |
| 乐观锁 | `UPDATE ... WHERE version = ?` |

**【推荐】** DELETE 应设计为幂等：删除不存在的资源返回成功或 404，不要报 500。

### 2.5 接口文档

**【推荐】** 用 OpenAPI 注解描述接口，让文档随代码走，避免手写文档过期。

```java
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Operation(summary = "查询用户详情")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @GetMapping("/{id}")
    public Result<UserResponse> getById(@PathVariable Long id) { ... }
}
```

**【强制】** 生产环境关闭或鉴权保护 Swagger UI。暴露的接口文档等于把攻击面清单交给攻击者。

### 2.6 兼容性

**【强制】** 已发布接口不做破坏性变更：不删字段、不改字段类型、不改字段语义、不增加必填参数。

**【推荐】** 新增字段用可选形式，老客户端忽略即可。

---

## 三、安全规范

### 3.1 认证设计（双 Token）

**【推荐】** access token + refresh token 分离，是无状态与可控性之间的平衡：

| | access token | refresh token |
|---|---|---|
| 有效期 | 短（5-30 分钟） | 长（7-30 天） |
| 用途 | 每次请求鉴权 | 换取新 access token |
| 存储 | 内存 / 前端变量 | 服务端持久化 jti（Redis） |
| 验证方式 | 纯验签，不查存储 | 验签 + 查存储 |
| 可否吊销 | 不可（靠短过期兜底） | 可（删存储记录即失效） |

设计要点：

**【强制】** token 中标记类型（`type: access` / `type: refresh`），验证时校验类型。否则 refresh token 可直接当 access token 用，长期有效的凭证被用于日常请求，等于绕过短过期设计。

**【强制】** refresh token 的 jti 存服务端（Redis），TTL 与 token 过期时间对齐。这是唯一的吊销手段。

**【强制】** refresh 时轮换（rotation）：旧 refresh token 立即失效，签发新的。可检测 token 被盗用——旧 token 再次出现说明泄漏。

**【推荐】** 滑动窗口 + 绝对上限双重控制：每次 refresh 续期，但从首次登录起超过绝对上限（如 30 天）必须重新登录。只有滑动窗口意味着 token 可无限续期，永不强制重新认证。

**【强制】** 登出必须删除服务端 refresh 记录。仅前端清 localStorage 不算登出，token 仍然有效。

### 3.2 Token 实现细节

**【强制】** 签名密钥长度满足算法要求（HS256 至少 256 bit），且从环境变量或密钥管理服务读取，**不硬编码、不提交仓库**。

```properties
# ✗ 提交进 git 的密钥等于公开
app.jwt.secret=Zm9vYmFyLXN1cGVyLXNlY3JldC1rZXk=

# ✓
app.jwt.secret=${JWT_SECRET}
```

**【强制】** 算法固定在服务端，不从 token header 读取。历史上的 `alg: none` 与 RS256→HS256 混淆攻击都源于信任客户端声明的算法。

**【强制】** 验签同时校验 `exp`，并考虑时钟偏移容忍（通常 ≤ 60 秒）。

**【强制】** JWT payload 是 Base64 编码而非加密，**任何人都能解开**。不要放敏感数据。

**【推荐】** payload 只放鉴权必需的最小信息：`sub`、`role`、`uid`、`exp`、`jti`。放大量业务数据会让 token 膨胀，且数据无法及时更新（改了角色但 token 里还是旧的）。

**【推荐】** 多服务共享验签密钥时，密钥轮换需要过渡期同时支持新旧密钥，否则轮换瞬间所有 token 失效。

### 3.3 密码存储

**【强制】** 密码必须加盐哈希，用专为密码设计的慢哈希算法（BCrypt / Argon2 / PBKDF2）。禁止 MD5、SHA-1、SHA-256——它们太快，适合校验完整性而非抵抗爆破。

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();   // 自带随机盐
}
```

**【强制】** 禁止明文存储、可逆加密存储密码，禁止日志打印密码。

**【强制】** 登录失败提示统一为"用户名或密码错误"，不区分"用户不存在"与"密码错误"。区分开会让攻击者枚举出有效用户名。

**【强制】** 密码比对用框架提供的 `matches()`，其内部为定长时间比较。手写 `equals()` 比较哈希存在时序侧信道风险。

### 3.4 授权

**【强制】** 默认拒绝。白名单放行少数公开端点，其余全部要求认证。

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated())   // 兜底：新增接口默认受保护
```

顺序很重要：规则从上往下匹配，宽泛规则写在前面会覆盖后面的严格规则。

**【强制】** 防越权（IDOR）。校验当前用户是否有权访问目标资源，不能只靠"前端不显示入口"。

```java
// ✗ 越权：任何登录用户都能查别人的订单
@GetMapping("/orders/{id}")
public Result<Order> get(@PathVariable Long id) {
    return Result.ok(orderService.findById(id));
}

// ✓ 校验归属
@GetMapping("/orders/{id}")
public Result<Order> get(@PathVariable Long id) {
    Long uid = UserContext.currentUserId();
    return Result.ok(orderService.findByIdAndUserId(id, uid));
}
```

**【强制】** 权限校验在服务端。前端隐藏按钮不是权限控制。

**【强制】** 认证/授权失败的响应形式与接口风格一致。REST API 返回 JSON 401/403，而不是重定向到登录页——重定向对 AJAX 调用无意义，前端拿到的是登录页 HTML 而非可解析的错误。

```java
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest req, HttpServletResponse resp,
                         AuthenticationException e) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(resp.getWriter(), Result.error(401, "未认证或 token 已过期"));
    }
}
```

### 3.5 传输与存储安全

**【强制】** 生产环境强制 HTTPS。token 走明文 HTTP 等于公开。

**【推荐】** refresh token 优先放 `HttpOnly` + `Secure` + `SameSite` Cookie，避免 XSS 读取。放 localStorage 的代价是任意 XSS 都能盗取长期凭证。

**【强制】** 无状态 JWT 且不使用 Cookie 时可关闭 CSRF；一旦用 Cookie 承载凭证，CSRF 防护必须打开。这两者是配套决策，不能只关一半。

**【强制】** CORS 不要在生产用 `allowedOrigins("*")`。配合 `allowCredentials(true)` 时该组合会被浏览器拒绝，且本身意味着任意站点可发起跨域请求。

```java
// ✗ 生产环境
config.setAllowedOrigins(List.of("*"));

// ✓ 显式白名单
config.setAllowedOrigins(List.of("https://app.example.com"));
config.setAllowCredentials(true);
```

### 3.6 限流与防护

**【强制】** 登录、注册、发短信、找回密码等接口必须限流。按 IP + 账号双维度，防止撞库与短信轰炸。

**【推荐】** 连续登录失败达阈值后锁定账号或要求验证码。

**【推荐】** 网关层统一做限流、鉴权前置校验，服务层保留轻量验签作为兜底——防止绕过网关直连内网服务。

**【强制】** 文件上传校验类型、大小、存储路径，禁止用客户端提供的文件名直接落盘（路径穿越）。

### 3.7 敏感信息管理

**【强制】** 仓库中不出现任何真实凭证：数据库密码、Redis 密码、JWT 密钥、第三方 AK/SK、证书私钥。用环境变量或配置中心。

**【强制】** 配置文件模板（`application-example.properties`）用占位符，真实配置文件加入 `.gitignore`。

**【强制】** 密钥一旦提交进 git，即使后续删除也视为已泄漏，必须轮换。git 历史保留所有版本。

---

## 评审检查清单

评审代码时按以下顺序过一遍：

**正确性**
- [ ] 边界条件：空集合、null、单元素、超大输入
- [ ] 并发安全：共享状态、ThreadLocal 清理、锁范围
- [ ] 事务边界：范围是否过大、是否含远程调用、回滚条件

**安全**
- [ ] 所有外部输入是否校验
- [ ] 是否存在 SQL 拼接
- [ ] 是否存在越权（能否访问他人数据）
- [ ] 敏感信息是否进了日志或响应体
- [ ] 是否有硬编码凭证

**性能**
- [ ] 循环内是否查库或调远程
- [ ] 列表查询是否分页且有上限
- [ ] 是否存在无界队列 / 无界线程池

**可维护性**
- [ ] 分层职责是否清晰，业务是否泄漏进 Controller
- [ ] 异常是否被吞、堆栈是否丢失
- [ ] 命名是否表意，注释是否解释了"为什么"
- [ ] 接口变更是否向后兼容

---

## 参考

- 《阿里巴巴 Java 开发手册》（嵩山版）
- OWASP Top 10 / OWASP API Security Top 10
- OWASP Cheat Sheet: JSON Web Token for Java
- RFC 6749 (OAuth 2.0)、RFC 7519 (JWT)、RFC 8725 (JWT Best Current Practices)
