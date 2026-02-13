# FancyHelper CI/CD 开发指南

本文档详细说明了 FancyHelper 项目的 CI/CD 配置、测试框架和代码质量工具的使用方法，帮助团队成员快速上手。

---

## 目录

- [概述](#概述)
- [本地开发环境配置](#本地开发环境配置)
- [测试框架](#测试框架)
- [代码质量工具](#代码质量工具)
- [CI/CD 工作流](#cicd-工作流)
- [常见问题](#常见问题)

---

## 概述

FancyHelper 项目集成了以下开发工具和 CI/CD 组件：

| 工具 | 用途 | 状态 |
|------|------|------|
| JUnit 5 | 单元测试框架 | ✅ 已启用 |
| Mockito | Mock 测试框架 | ✅ 已启用 |
| JaCoCo | 代码覆盖率统计 | ✅ 已启用 |
| SpotBugs | 静态代码分析 | ✅ 已启用 |
| PMD | 代码质量检查 | ✅ 已启用 |
| OWASP Dependency-Check | 安全漏洞扫描 | ✅ 已启用 |

### 项目结构

```
FancyHelper/
├── pom.xml                          # Maven 配置文件
├── checkstyle.xml                   # Checkstyle 规则配置
├── spotbugs-exclude.xml             # SpotBugs 排除规则
├── src/
│   ├── main/java/                   # 源代码
│   └── test/java/                   # 测试代码
│       └── org/YanPl/model/
│           ├── TodoItemTest.java    # TodoItem 测试类
│           └── AIResponseTest.java  # AIResponse 测试类
└── .github/workflows/
    └── CI-Build-Release.yml         # CI/CD 工作流配置
```

---

## 本地开发环境配置

### 前置要求

- **JDK**: 17 或 21（推荐 17）
- **Maven**: 3.8+
- **IDE**: IntelliJ IDEA（推荐）或 Eclipse

### 配置步骤

1. **安装 JDK**

   ```bash
   # 检查 Java 版本
   java -version
   ```

2. **安装 Maven**

   ```bash
   # 检查 Maven 版本
   mvn -version
   ```

3. **IDEA 配置（推荐）**

   - 导入项目为 Maven 项目
   - 确认 SDK 配置为 Java 17
   - 启用注解处理（Lombok 如有需要）

---

## 测试框架

### JUnit 5 基础

项目使用 JUnit 5（Jupiter）作为单元测试框架。

#### 依赖配置（pom.xml）

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-params</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
```

#### 基本测试示例

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class ExampleTest {

    @Test
    @DisplayName("简单测试示例")
    void simpleTest() {
        int result = 2 + 2;
        assertEquals(4, result, "2 + 2 应该等于 4");
    }

    @Test
    @DisplayName("异常测试")
    void exceptionTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            throw new IllegalArgumentException("非法参数");
        });
    }
}
```

#### 参数化测试

使用 `@ParameterizedTest` 和 `@CsvSource` 进行多组数据测试：

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ParameterizedExample {

    @ParameterizedTest
    @CsvSource({
        "pending, PENDING",
        "in_progress, IN_PROGRESS",
        "completed, COMPLETED"
    })
    @DisplayName("测试状态字符串解析")
    void testStatusParsing(String input, TodoItem.Status expected) {
        TodoItem.Status result = TodoItem.Status.fromString(input);
        assertEquals(expected, result);
    }
}
```

### Mockito Mock 测试

Mockito 用于模拟依赖对象，隔离测试环境。

#### 依赖配置（pom.xml）

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.11.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <version>5.11.0</version>
    <scope>test</scope>
</dependency>
```

#### Mock 测试示例

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceTest {

    @Mock
    private Dependency dependency;

    @Test
    @DisplayName("Mock 对象行为验证")
    void testMockBehavior() {
        // 设置 mock 对象行为
        when(dependency.getData()).thenReturn("mocked data");

        // 调用测试方法
        String result = dependency.getData();

        // 验证结果
        assertEquals("mocked data", result);

        // 验证方法调用次数
        verify(dependency, times(1)).getData();
    }
}
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=TodoItemTest

# 运行特定测试方法
mvn test -Dtest=TodoItemTest#testStatusParsing

# 生成测试报告
mvn surefire-report:report
```

---

## 代码质量工具

### JaCoCo 代码覆盖率

JaCoCo 用于统计代码测试覆盖率。

#### 配置（pom.xml）

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

#### 使用方法

```bash
# 运行测试并生成覆盖率报告
mvn test

# 查看报告
open target/site/jacoco/index.html
```

#### 覆盖率目标

- **行覆盖率**: ≥ 70%
- **分支覆盖率**: ≥ 60%
- **类覆盖率**: ≥ 80%

### SpotBugs 静态分析

SpotBugs 用于检测 Java 代码中的潜在 bug。

#### 配置（pom.xml）

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.8.3.1</version>
    <configuration>
        <excludeFilterFile>spotbugs-exclude.xml</excludeFilterFile>
    </configuration>
</plugin>
```

#### 排除规则（spotbugs-exclude.xml）

```xml
<FindBugsFilter>
    <!-- 排除 Bukkit 插件特定的静态方法调用警告 -->
    <Match>
        <Bug pattern="ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"/>
    </Match>
</FindBugsFilter>
```

#### 使用方法

```bash
# 运行 SpotBugs 检查
mvn spotbugs:check

# 生成 HTML 报告
mvn spotbugs:spotbugs
open target/spotbugs.html
```

### PMD 代码质量检查

PMD 用于检查代码质量问题（如复杂度过高、未使用的变量等）。

#### 配置（pom.xml）

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <version>3.21.2</version>
    <configuration>
        <rulesets>
            <ruleset>/rulesets/java/quickstart.xml</ruleset>
        </rulesets>
    </configuration>
</plugin>
```

#### 使用方法

```bash
# 运行 PMD 检查
mvn pmd:check

# 生成 HTML 报告
mvn pmd:pmd
open target/site/pmd.html
```

---

## CI/CD 工作流

### 工作流概览

CI/CD 工作流分为四个阶段，按顺序执行：

```
check (代码检查) → test (单元测试) → security (安全扫描) → build (构建发布)
```

### Job 详解

#### 1. check - 代码检查

检查代码风格和质量，包括：
- Checkstyle 代码风格检查
- SpotBugs 静态分析
- PMD 代码质量检查

**触发条件**: Pull Request、Push 到 master/dev 分支、手动触发（未启用 skip_checks）

#### 2. test - 单元测试

运行所有单元测试并生成覆盖率报告。

**构建矩阵**: 同时测试 Java 17 和 Java 21

**输出产物**:
- 测试报告（test-report-java17/test-report-java21）
- 覆盖率报告（coverage-report）

#### 3. security - 安全扫描

使用 OWASP Dependency-Check 扫描依赖中的安全漏洞。

**输出产物**: 依赖安全报告（dependency-check-report）

#### 4. build - 构建发布

只有在所有检查通过后才执行构建。

**构建类型**:
- **快照包**: 日常提交、Pull Request
- **正式包**: 推送 Tag、手动触发 release 类型

### 手动触发工作流

在 GitHub Actions 页面点击 "Run workflow" 可以手动触发：

| 参数 | 说明 |
|------|------|
| build_type | `snapshot`（快照包）或 `release`（正式包） |
| custom_version | 仅 release 类型生效，自定义版本号 |
| skip_checks | 跳过代码检查（仅用于紧急发布） |

### 发布流程

#### 自动发布（推荐）

1. 在 master 分支打 Tag：
   ```bash
   git tag v3.4.0
   git push origin v3.4.0
   ```

2. CI/CD 自动触发构建并发布 Release

#### 手动发布

1. 在 GitHub Actions 页面点击 "Run workflow"
2. 选择 `build_type: release`
3. 填写 `custom_version`（如 `3.4.0`）
4. 点击运行

---

## 常见问题

### Q1: 测试失败如何调试？

**A**: 查看测试报告获取详细信息：

```bash
# 生成测试报告
mvn surefire-report:report

# 查看报告
open target/site/surefire-report.html
```

### Q2: 如何跳过某个质量检查？

**A**: 在构建命令中添加跳过参数：

```bash
# 跳过 SpotBugs
mvn package -Dspotbugs.skip=true

# 跳过 PMD
mvn package -Dpmd.skip=true

# 跳过测试
mvn package -DskipTests
```

### Q3: 如何添加新的测试类？

**A**:

1. 在 `src/test/java/org/YanPl/` 下创建测试类
2. 继承或使用 JUnit 5 注解
3. 使用 `@Test` 标记测试方法
4. 运行 `mvn test` 验证

### Q4: SpotBugs 报告误报怎么办？

**A**: 在 `spotbugs-exclude.xml` 中添加排除规则：

```xml
<Match>
    <Class name="org.YanPl.SomeClass"/>
    <Bug pattern="特定错误代码"/>
</Match>
```

### Q5: 如何提高代码覆盖率？

**A**:

1. 使用 JaCoCo 查看未覆盖的代码：
   ```bash
   mvn test
   open target/site/jacoco/index.html
   ```

2. 为红色标记的代码添加测试用例

3. 覆盖率目标：行覆盖率 ≥ 70%

### Q6: CI/CD 工作流失败如何排查？

**A**:

1. 查看 GitHub Actions 日志
2. 下载失败 job 的产物（测试报告、检查报告）
3. 本地复现问题：
   ```bash
   # 复现 check job
   mvn checkstyle:check spotbugs:check pmd:check

   # 复现 test job
   mvn test
   ```

### Q7: 如何在本地运行完整的 CI 检查？

**A**:

```bash
# 运行所有检查
mvn clean test checkstyle:check spotbugs:check pmd:check
```

---

## 参考资料

- [JUnit 5 用户指南](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito 文档](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [JaCoCo 官方文档](https://www.jacoco.org/jacoco/trunk/doc/)
- [SpotBugs 文档](https://spotbugs.github.io/)
- [PMD 文档](https://pmd.github.io/)
- [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/)

---

**文档版本**: 1.0  
**最后更新**: 2026-02-13  
**维护者**: FancyHelper 开发团队