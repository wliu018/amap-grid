# 高德 POI 动态网格探测与全市网格生成

Java 17 / Spring Boot 3.5.6 / Maven / PostgreSQL / MyBatis-Plus 3.5.12 /
Fastjson2 2.0.57 / OkHttp 4.12.0 / JTS 1.20.0 / Lombok / Validation / SLF4J。

当前实现行政区解析、POI count 探测、统一网格重建、稳定编号、随机任务顺序和批次持久化。
不实现全量分页采集、POI 数据表、扩展字段、PostGIS 或多 Worker。

## 目录

```text
amap-grid/
├── pom.xml
├── README.md
├── .gitignore
├── .github/workflows/build.yml
├── src/main/java/com/example/amap/
│   ├── AmapApplication.java
│   ├── client/
│   │   ├── AmapClient.java
│   │   └── AmapRequestRateLimiter.java
│   ├── config/
│   │   ├── AmapProperties.java
│   │   ├── FastjsonWebConfig.java
│   │   ├── MybatisPlusConfig.java
│   │   └── OkHttpConfig.java
│   ├── controller/AmapGridController.java
│   ├── dto/
│   │   ├── amap/{AmapDistrictResponse,AmapPoiSearchResponse}.java
│   │   ├── request/GridInitRequest.java
│   │   └── response/{ApiResponse,GridInitResponse}.java
│   ├── entity/{AmapGrid,AmapGridProbe,AmapGridConfig,AmapGridBatch}.java
│   ├── mapper/{AmapGridMapper,AmapGridProbeMapper,AmapGridConfigMapper,AmapGridBatchMapper}.java
│   ├── service/
│   │   ├── AmapDistrictService.java
│   │   ├── AmapGridProbeService.java
│   │   ├── AmapGridService.java
│   │   ├── AmapGridConfigService.java
│   │   ├── AmapGridBatchService.java
│   │   ├── GridInitializeService.java
│   │   ├── ProbeRecordService.java
│   │   ├── BatchStateService.java
│   │   ├── BatchPersistenceService.java
│   │   └── impl/（六个领域 Service 实现）
│   ├── model/{GeoPoint,GridCell,CountedGrid,ProbeResult}.java
│   ├── util/{GeoUtils,GridUtils,GeometryUtils}.java
│   ├── enums/{GridStatus,GridBatchStatus,ProbeStatus}.java
│   ├── exception/{BusinessException,GlobalExceptionHandler}.java
│   └── handler/MyMetaObjectHandler.java
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql
│   └── mapper/{AmapGridMapper,AmapGridConfigMapper}.xml
└── src/test/java/com/example/amap/
    ├── PostgresIntegrationTest.java
    ├── client/AmapClientTest.java
    ├── service/{GridProbeTest,GridInitializeTest}.java
    └── util/GeometryAndGridTest.java
```

## 创建数据库与建表

建议 PostgreSQL 14+。例如：

```bash
createdb -h 127.0.0.1 -U postgres amap_poi
psql -h 127.0.0.1 -U postgres -d amap_poi -f src/main/resources/schema.sql
```

也可以运行：

```sql
CREATE DATABASE amap_poi;
```

切换连接至 amap_poi 后执行 schema.sql。脚本可重复执行，不删除现有数据；
已有表的结构变更需要单独迁移。应用默认 spring.sql.init.mode=never，不自动操作表结构。
完整脚本见 [schema.sql](src/main/resources/schema.sql)。

## 配置与运行

先安装 JDK 17、Maven 3.6.3+。POSIX shell：

```bash
export AMAP_KEY='你的高德 Web 服务 Key'
export DB_PASSWORD='你的数据库密码'
# 以下为可选项：
export DB_URL='jdbc:postgresql://127.0.0.1:5432/amap_poi'
export DB_USERNAME='postgres'

mvn clean package
java -jar target/amap-grid-1.0.0.jar
# 或 mvn spring-boot:run
```

PowerShell 设置方式：`$env:AMAP_KEY='...'`、`$env:DB_PASSWORD='...'`。
Key 必须具备相应行政区接口和 v5 polygon POI 接口权限与额度。
所有测试不请求真实高德 API，默认构建不需要真实 AMAP_KEY 或数据库。

完整配置见 [application.yml](src/main/resources/application.yml)。

| 参数（amap.poi-grid） | 默认 | 含义 |
| --- | --- | --- |
| region | 310000 | 请求不传 region 时的 adcode |
| types | 050000 | 请求不传 types 时的 POI 分类 |
| city-limit | true | 必须为 true，始终同时传 region |
| show-fields | false | false 完全不传 show_fields；true 仅发送空值，不启用扩展字段 |
| initial-grid-size-meter | 5000 | 全市第一轮粗网格米数 |
| safe-count | 100 | TOP N 都不超过此值时停止 |
| min-grid-size-meter | 250 | 最小推荐米数，达到后一定停止 |
| top-density-grid-count | 5 | 每轮统一选取的最高密度网格数 |
| page-size | 25 | 探测仅 page_num=1，合法范围1–25 |
| request-interval-ms | 1100 | 全进程高德请求开始时间的最小间隔 |
| retry-count | 3 | 首次失败后最多3次重试，共最多4次请求 |
| retry-backoff-ms | [1000,3000,10000] | 重试退避毫秒；更多重试重复最后一个间隔 |
| database-batch-size | 500 | 每条多 VALUES INSERT 的行数，上限1000 |

amap.base-url、connect-timeout-seconds、read-timeout-seconds 也通过 ConfigurationProperties 绑定。
业务计算使用 double，实体使用 BigDecimal，数据库 NUMERIC(12,8) 在落库时定标。
region 必须六位数字，types 为六位类型码或用 `|` 连接的多个类型码。
显式传空字符串报400，不静默回退配置。

## 调用接口

```bash
curl -X POST http://localhost:8080/api/amap/grid/init \
  -H 'Content-Type: application/json' \
  -d '{"region":"310000","types":"050000","force":false}'
```

省略参数使用默认值：

```bash
curl -X POST http://localhost:8080/api/amap/grid/init \
  -H 'Content-Type: application/json' -d '{}'
```

force=false：若 currentBatchNo 指向匹配 region/types 的 SUCCESS 批次，直接返回。
force=true：重新探测和生成；不删除旧批次。初始化为同步接口，耗时主要取决于第一层
有效网格数量和请求间隔。客户端或反向代理需要相应设置超时；客户端断开不一定取消服务端工作。
不要因 HTTP 客户端超时立即反复 force=true。

成功数据结构（下列数值仅示例）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "region": "310000",
    "types": "050000",
    "recommendedGridSize": 625,
    "gridCount": 12683,
    "insideGridCount": 12031,
    "boundaryGridCount": 652,
    "maxProbeCount": 92,
    "batchNo": "20260930152800123_a4b6c8d0e2f4"
  }
}
```

参数错误400；高德/几何错误502；数据库错误500。
错误响应同样使用 ApiResponse，不回显 Key、密码、请求 URL 或原始数据库异常。

## 动态探测算法

1. 使用 v3 行政区接口，以 adcode 精确匹配 districts。按 `|` 分开 Polygon，
   按 `;` 解析坐标。自动闭环，多片构成 MultiPolygon，JTS 修复无效几何后再次验证。
   坐标保持高德返回的 GCJ-02，经纬度用于平面 JTS 关系判断，不伪造 EPSG 坐标系统。
2. 从整个城市 BoundingBox 开始按米生成第一层网格，绝不只探测中心。
   纬度步长 meter/111320；每行使用该行中心纬度计算经度步长
   meter/(111320*cos(latitude))。边缘格不裁短，以保持实际目标米数并覆盖完整 BoundingBox。
3. 保留 intersects 的网格，逐个调用 v5 /place/polygon，只获取合法 count。
   参数包括 polygon、types、region、city_limit=true、page_size、page_num=1。
   polygon 点顺序固定左下、右下、右上、左上。每次实际尝试（含重试）保存 SUCCESS/FAILED 记录。
   失败无法转换为0；全部重试失败中止初始化。
4. 按 count DESC 统一排序，取 TOP N；全部 <= safeCount 则推荐当前尺寸。
   否则所有 TOP N 父格按实际经纬度中点四等分，无面积重叠、无缝隙，保留城市交集子格。
   所有子格统一竞争下一轮 TOP N，不能每个父格各选一个。
5. 下一推荐尺寸 max(currentSize/2,minSize)，整数向下取整，示例5000→2500→1250→625→312→250。
   **最小截断修正**：312米父格四等分的实际子格约156米，不能仅改标签成250米。
   在截断轮仍请求四个子格并以156米名义记录，然后以真实250米网格覆盖全部TOP N父区域，
   查询这些250米格并统一竞争、生成最终 maxProbeCount。此时追加探测格可超过4×TOP N。
   250米仍超 safeCount 时记录 region/types/gridSize/maxCount/safeCount 的 WARN 并继续生成。
   整数半尺寸与真实中点切分存在小数米差异，记录米数为名义整数，完整实际范围保存在坐标字段。
6. **丢弃探测网格作为最终任务的做法**：以 recommendedGridSize 从城市 BoundingBox
   重新生成完整统一网格，并过滤完全外部网格。contains=2（INSIDE），其他交集=1（BOUNDARY）。
7. gridNo 用 region/types/row/col 决定，例如310000_050000_R00001_C00002。
   同一边界、参数和尺寸重复生成稳定；行政区边界发生变化时编号也可能随 BoundingBox 改变。
   多类型允许100字符，grid_no 列设为140以容纳完整类型串和行列编号。
8. shuffle 只调整列表顺序，然后 randomOrder=1..N；坐标、行列、gridNo 不改变。

该 TOP N 方法提供基于重点区域抽样的尺寸推荐，不能数学上证明全市所有最终格都 <=safeCount：
未晋级区域、重新铺网的偏移以及高德 count 截断都可能影响结果。最小保护下也可能仍不安全。
下一阶段采集应检查每格返回计数/接口分页上限，并明确处理超限。当前阶段严格不执行全市分页采集。

## 事务和表

| 表 | 用途 |
| --- | --- |
| amap_grid_probe | 全部层级、成功/失败及重试尝试的过程记录 |
| amap_grid_config | region+poi_type 唯一的推荐尺寸及 currentBatchNo |
| amap_grid_batch | CREATING / SUCCESS / FAILED、统计及错误信息 |
| amap_grid | 最终稳定编号、坐标、关系、randomOrder、WAITING状态 |

普通 CRUD 通过 MyBatis-Plus BaseMapper / IService；查询更新使用 Lambda Wrapper。
网格使用 Mapper XML 真正的多 VALUES 分批 INSERT，避免逐行写入；
配置使用 PostgreSQL ON CONFLICT 原子 UPSERT。

先用独立短事务登记 CREATING 批次，便于即使行政区查询失败也保留批次状态和关联探测记录。
所有高德 HTTP 期间没有长事务。每次探测通过单独 Bean 的 REQUIRES_NEW 保存。
最终生成完成后，BatchPersistenceService 的事务保存全体网格、SUCCESS状态和UPSERT配置。
任意一步失败全部回滚，currentBatchNo 不切换；独立短事务再将新批次标 FAILED。
若数据库本身不可用，FAILED 状态可能无法落库，会明确记录日志；恢复后可检查残留 CREATING。
UUID短后缀与数据库唯一约束防批次碰撞，batchNo不是主键，id仍为BIGSERIAL。

同进程相同 region/types 初始化串行执行；限速组件覆盖整个 HTTP 调用，所有城市共享限速。
本阶段面向单服务实例。多实例的全局限速与同键初始化协调未实现；并发最终提交仍由数据库事务
保证每个 currentBatchNo 指向完整成功批次，最后完成的事务决定当前批次。

## 验证

```bash
mvn clean package
```

默认执行算法、客户端MockWebServer、复用/参数覆盖等单元测试，PostgreSQL集成测试跳过。
涵盖米换算、四等分覆盖/无面积重叠/无缝隙、闭环、MultiPolygon、intersects、
relationType、TOP N、阈值和最小停止、真实最小米数、稳定编号和randomOrder唯一性。

GitHub Actions 使用 Java17 和独立 PostgreSQL16，执行同一条 mvn -B clean package，
启用集成测试检查 Bean注入、Mapper XML、SQL、批量保存、配置UPSERT、Fastjson MVC响应及
最终事务失败时旧currentBatchNo/独立探测记录不变。CI上传可执行jar和Surefire报告。
Maven Enforcer 禁止 Jackson、Gson、Fastjson1、JPA/Hibernate及另一套MyBatis Starter依赖。

手动运行数据库集成测试（**只用可清空的专用测试数据库；会清空四张表**）：

```bash
export DB_IT=true
export DB_URL='jdbc:postgresql://127.0.0.1:5432/amap_poi_test'
export DB_USERNAME=postgres
export DB_PASSWORD='测试数据库密码'
mvn clean package
```

## 后续 POI 采集接入

从 config.currentBatchNo 获取当前成功批次，按 batch_no/status/random_order 查询任务。
AmapGridMapper.selectWaiting 已预留基础查询。将来多Worker可在短事务中调用 lockWaiting，
使用 FOR UPDATE SKIP LOCKED，更新任务为 RUNNING 后提交，再执行 HTTP；HTTP不能持有行锁。
采集成功/失败再独立更新 SUCCESS/FAILED。本阶段没有实现 Consumer、POI表或分页采集。
