# 实施计划：[功能名称]

**分支**：`[###-feature-name]` | **日期**：[DATE] | **规格**：[链接]

**输入**：`/specs/[###-feature-name]/spec.md` 中的功能规格

**说明**：本模板由 `$speckit-plan` 填写；具体执行流程以该技能定义为准。

## 摘要

[从功能规格提取：主要需求及技术方案]

## 技术背景

<!--
  待处理：将本节替换为项目真实的技术信息。
  当前结构用于指导规划迭代，可根据项目情况调整。
-->

**语言/版本**：[例如 Java 17、Python 3.11，或 NEEDS CLARIFICATION]

**主要依赖**：[例如 Spring Boot、FastAPI，或 NEEDS CLARIFICATION]

**存储**：[如适用，例如 PostgreSQL、文件，或 N/A]

**测试**：[例如 JUnit、pytest，或 NEEDS CLARIFICATION]

**目标平台**：[例如 Linux 服务端、iOS 15+，或 NEEDS CLARIFICATION]

**项目类型**：[例如库、CLI、Web 服务、移动应用，或 NEEDS CLARIFICATION]

**性能目标**：[领域相关目标，例如 1000 req/s、p95 小于 200 ms，或 NEEDS CLARIFICATION]

**约束**：[领域相关约束，例如内存小于 100 MB、支持离线，或 NEEDS CLARIFICATION]

**规模/范围**：[例如 1 万名用户、50 个页面，或 NEEDS CLARIFICATION]

## 宪章检查

*质量门：Phase 0 研究开始前必须通过，并在 Phase 1 设计完成后重新检查。*

[根据宪章文件确定质量门]

## 项目结构

### 当前功能的文档

```text
specs/[###-feature]/
├── plan.md              # 本文件（由 $speckit-plan 生成）
├── research.md          # Phase 0 输出（由 $speckit-plan 生成）
├── data-model.md        # Phase 1 输出（由 $speckit-plan 生成）
├── quickstart.md        # Phase 1 输出（由 $speckit-plan 生成）
├── contracts/           # Phase 1 输出（由 $speckit-plan 生成）
└── tasks.md             # Phase 2 输出（由 $speckit-tasks 生成，不由 $speckit-plan 创建）
```

### 源代码（仓库根目录）

<!--
  待处理：将下面的占位目录树替换为当前功能的真实结构。
  删除未使用的选项，并使用真实路径扩展选中的结构。
  最终计划中不得保留“选项”标签。
-->

```text
# [未使用时删除] 选项 1：单体项目（默认）
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [未使用时删除] 选项 2：Web 应用（检测到前端和后端时）
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [未使用时删除] 选项 3：移动端与 API（检测到 iOS 或 Android 时）
api/
└── [结构同上方后端]

ios/ 或 android/
└── [平台相关结构：功能模块、界面流程、平台测试]
```

**结构决策**：[记录选择的结构，并引用上面列出的真实目录]

## 复杂度跟踪

> 仅当宪章检查存在必须说明理由的违规项时填写。

| 违规项 | 必要性 | 未采用更简单方案的原因 |
| --- | --- | --- |
| [例如第 4 个项目] | [当前需求] | [为什么 3 个项目不足] |
| [例如 Repository 模式] | [具体问题] | [为什么直接访问数据库不足] |
