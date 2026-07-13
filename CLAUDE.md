# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

一个基于 Spring Boot 4.1.0 的排序算法可视化/演示项目（初始阶段），使用 Java 21 和 Maven 构建。

## 构建与运行命令

```bash
# 构建项目（跳过测试）
mvn clean compile -DskipTests

# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=SortingApplicationTests

# 运行单个测试方法
mvn test -Dtest=SortingApplicationTests#contextLoads

# 打包
mvn clean package -DskipTests

# 运行 Spring Boot 应用
mvn spring-boot:run

# 以开发模式运行（自动重启）
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.devtools.restart.enabled=true"
```

## 项目结构

```
sorting/
├── pom.xml                          # Maven 配置，Spring Boot 4.1.0 + Java 21
├── src/
│   ├── main/
│   │   ├── java/com/example/sorting/
│   │   │   └── SortingApplication.java   # Spring Boot 入口
│   │   └── resources/
│   │       └── application.yaml          # 应用配置
│   └── test/
│       └── java/com/example/sorting/
│           └── SortingApplicationTests.java  # 上下文加载测试
├── HELP.md                          # Spring Boot 初始文档

└── .gitignore
```

# Superpowers 强制流程
1. 所有开发需求自动启用 using-superpowers；
2. 必须调用 brainstorming 澄清需求；
3. 必须调用 writing-plans 生成 .claude/plans/ 任务计划文件；
4. 禁止跳过规划直接写代码，所有步骤输出简体中文。

## 语言强制规则
1. 全部对话、分析、文档、代码注释使用简体中文；
2. 仅编程语言关键字、第三方库名称保留英文；
3. 报错信息、修改建议、逻辑说明统一中文；
4. 禁止中英文穿插混杂输出。

## 技术栈

- **Java 21** — 支持虚拟线程、Record、Switch 模式匹配等最新特性
- **Spring Boot 4.1.0** — 最新一代 Spring Boot（基于 Spring Framework 7.x）
- **Maven** — 构建工具
- **JUnit 5** — 测试框架（Spring Boot Starter Test 内置）

## 设计约定

- 遵循 Spring Boot 常规分层架构：controller → service → repository（按需扩展）
- 测试使用 `@SpringBootTest` 进行集成测试，JUnit 5 进行单元测试
- 配置放在 `application.yaml` 中（非 properties 格式）
- 所有时间信息均使用 UTC 时间，并且统一格式
- 项目中使用了lombok插件，尽量使用lombok注解简化实体类
