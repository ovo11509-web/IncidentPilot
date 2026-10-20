# 来源 HMAC v1 签名契约

**协议标识**：`incident-hmac-v1`\
**算法**：HMAC-SHA256\
**适用路由**：机器来源提交和机器来源状态查询

本文件固化 DEC-013/DEC-014 要求的精确线协议。实现和测试不得使用另一种“等价”字符串拼接。

## 1. 请求头

| HTTP 头 | 规则 |
| --- | --- |
| `X-Incident-Protocol-Version` | 必填；语法为 `0` 或不带前导零的 1～3 位十进制 ASCII；当前唯一支持值为 `1`。语法有效但不支持的值先参与验签，通过后返回 `422` |
| `X-Incident-Source-Id` | 必填；1～128 个 ASCII 字符，匹配 `[a-z0-9][a-z0-9._-]{0,127}`；必须与注册表完全匹配 |
| `X-Incident-Key-Id` | 必填；1～64 个 ASCII 字符，匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`；只选择该来源下同名且活动的密钥，不回退尝试其他密钥 |
| `X-Incident-Request-Timestamp` | 必填；UTC Unix epoch 秒的最短十进制 ASCII 表示，不允许符号、空白或前导零（值 `0` 除外） |
| `X-Incident-Signature` | 必填；精确格式 `sha256=` + 64 位小写十六进制 |

头值不得先做 trim 后接受。包含首尾空白、重复头、多值头、非 ASCII 或不符合上述规范形式时，统一按认证失败处理。

## 2. 规范方法

- 只接受契约声明的方法：提交为 `POST`，状态查询为 `GET`。
- 进入签名输入的方法为服务器实际收到方法的 ASCII 大写形式。
- 不允许方法覆盖头或查询参数改变方法。

## 3. 规范路径

签名使用 OpenAPI 中的应用路由路径，不含部署 servlet context `/api/ragent`、scheme、host、端口、查询串或 fragment：

```text
POST /incident/v1/alerts
GET  /incident/v1/alerts/{alertId}
```

状态查询中的 `{alertId}` 替换为实际目标 ID。v1 `alertId` 必须匹配 `[0-9]{1,20}`，因此路径不需要百分号解码。

请求路径必须精确匹配以上形式。以下形式均不做宽松归一化并在返回业务数据前拒绝：

- 末尾斜杠；
- 重复斜杠、`.` 或 `..` 段；
- 百分号编码的路径段或分隔符；
- 分号矩阵参数；
- 任意查询参数；
- 经代理重写后无法恢复为上述应用路由的路径。

部署层可以增加固定 context path，但 HMAC 客户端始终签署本节的应用路由；服务端在路由匹配后从已知模板和已验证 path variable 构造同一规范路径，不直接信任转发头。

## 4. 请求体字节

- `POST /incident/v1/alerts` 使用服务器实际读取的原始请求体字节；不得重新编码或重新序列化 JSON。
- 原始体必须是 UTF-8 JSON，实际长度最多为配置上限，默认 65,536 字节。
- `GET` 的规范请求体是长度为零的空字节序列；带有任意请求体字节的 GET 不是有效 v1 请求。
- `Content-Length` 不进入签名输入，也不替代实际读取长度。

## 5. 待签名字节编码

待签名消息由七个组件按下列顺序组成：

1. ASCII `incident-hmac-v1`
2. 协议版本头的原始 ASCII 字节（当前支持的 v1 为 `1`；其他语法有效值也按原值签署）
3. 来源 ID 的 ASCII 字节
4. 请求时间戳的 ASCII 字节
5. 规范方法的 ASCII 字节
6. 规范路径的 ASCII 字节
7. 实际请求体字节

每个组件编码为：

```text
uint32_be(byte_length) || component_bytes
```

其中 `uint32_be` 是无符号 32 位大端整数，`||` 表示直接连接。消息为七个编码组件的直接连接，没有换行、NUL、分隔字符、BOM 或尾随字节。

伪代码：

```text
message = frame(ascii("incident-hmac-v1"))
       || frame(ascii(protocolVersion))
       || frame(ascii(sourceId))
       || frame(ascii(requestTimestamp))
       || frame(ascii(upper(actualHttpMethod)))
       || frame(ascii(canonicalApplicationPath))
       || frame(rawBodyBytes)

signatureBytes = HMAC_SHA256(selectedSourceKey, message)
signatureHeader = "sha256=" + lowercaseHex(signatureBytes)
```

`keyId` 用于选择唯一候选密钥，不进入消息组件。攻击者把它改成其他活动 key 时，摘要必须因密钥不同而验证失败；实现不得在失败后枚举或回退到其他 key。

## 6. 验证顺序

### Webhook 提交

1. 精确接受 `application/json`，可带 `charset=utf-8`；其他媒体类型返回 `415`。
2. 有界读取实际请求体；超过上限返回 `413`。
3. 校验必需认证头的规范形式、来源/key 活动状态、时间窗口和 HMAC；任一失败统一返回 `401`。
4. 解析 JSON 并执行 UTF-8、深度、重复键和顶层对象检查；失败返回 `400`。
5. 校验协议版本和业务 Schema；失败返回 `422`。
6. 才允许进入告警持久化事务。

步骤 3 的时效比较使用数据库/服务端可信 UTC 当前时间；默认允许闭区间 `[now-300s, now+300s]`。边界值有效。

### 机器状态查询

1. 校验路径形式、空请求体和必需认证头。
2. 校验来源/key 活动状态、闭区间时效和 HMAC；失败返回 `401`。
3. 已认证但不支持的协议版本返回 `422`。
4. 查询 Alert，并验证 Alert 的 `sourceId` 等于认证来源。
4. 不存在或所有权不匹配均返回相同 `404`，不得泄露其他来源的 ID 是否存在。

## 7. 密钥轮换

- 注册表可为同一来源同时标记多个活动 key ID，形成配置的重叠期。
- 请求只使用 `X-Incident-Key-Id` 精确指定的一个活动 key 验证。
- 未知、已停用或属于其他来源的 key ID 一律作为 `401`，响应不得区分原因。
- 轮换不改变 `sourceId`、幂等身份、指纹或既有 Alert。

## 8. 比较、日志与响应安全

- 解码签名后使用常量时间字节比较，例如 JDK `MessageDigest.isEqual`。
- HMAC 密钥、签名值、待签名字节、完整原始请求体和派生密钥材料不得写日志、响应、审计、Timeline 或 Outbox。
- 日志最多包含请求 `requestId`、声明的安全来源 ID、key ID、路由模板和统一认证失败码；不得记录能够作为认证 oracle 的细分失败原因。
- 所有认证失败使用同一安全响应结构；内部指标可以按低基数失败阶段聚合，但不能带签名或载荷标签。

## 9. 必测向量

契约测试必须使用固定 key、固定头、固定路径和固定 UTF-8 body 生成至少一组可跨语言复现的十六进制消息与摘要向量，并覆盖：

- 方法从 POST 改为 GET；
- 固定路由改为其他路径；
- 查询 `alertId` 改变；
- body 任一字节改变、键顺序改变或只改变 JSON 空白；
- 空体与非空体；
- 时间窗口两个包含边界和边界外一秒；
- 活动 key、轮换期第二个活动 key、未知 key 和停用 key；
- 重复/多值认证头、前导零时间戳和大写十六进制签名。

除业务幂等对规范 JSON 的等价判断外，任何原始 body 字节变化都会改变 HMAC；二者是不同层次的规则。
