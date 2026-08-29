# SalaryFlow — 技术设计

## 技术栈

- Web / Android: React + Vite + TypeScript + Capacitor
- Shared Core: TypeScript package
- Persistence: localStorage（MVP）

## Monorepo

```text
salary-flow/
├── apps/web/
├── packages/core/
├── docs/
└── .github/workflows/
```

## 设计原则

1. 核心计算只写一次，位于 `@salary-flow/core`。
2. UI 只负责呈现与本地状态。
3. Web 与 Android 共用同一套界面和计算核心。
4. 所有实时数字都由时间戳推导。
5. local-first，MVP 不依赖数据库、账号或服务端 API。
