---
name: skill-creator
description: 创建新的技能(Skill)、修改和改进现有技能。当用户提到"创建技能"、"写个技能"、"做个助手"、"帮我做一个XX"、"我想让AI学会XX"、"把流程变成技能"时使用此技能，即使用户没有明确说出"技能"二字也应触发。也可用于编辑、优化已有技能。
---

# 技能创建助手

帮助用户创建和管理技能(Skill)，让 AI 能够更好地完成特定任务。

## 运行环境与工具（先读这一节）

本 App 以链式 Agent 执行本技能：一次任务可连续多轮调用工具，前一个工具的结果会回传，直到任务完成。

- `skill_lookup`：`{"name": "技能名"}`，读取已有技能的完整 skill.md 正文与配套文件列表。修改技能前必须先调用它。
- `create_skill`：`{"name": "...", "description": "...", "content": "完整 skill.md（以 --- 开头）", "references": {"references/xxx.md": "内容"}}`
- `update_skill`：`{"skill_name": "...", "content": "新的完整 skill.md"}`；只改某个配套文件时传 `reference_name` + `reference_content`
- `question`：向用户确认方案或保存，格式为 `{"questions": [{"question": "...", "header": "...", "options": [{"label": "...", "description": "..."}]}]}`
- `file_read` / `file_write`：读写当前书稿的虚拟文件，本技能一般不使用

重要限制（本 App 的 Skill 机制）：
- 主文件统一命名为 `skill.md`，不要写成 `SKILL.md` 或 `main.md`。
- 只支持 `references/` 下的 `.md` / `.txt` 文本配套文件；本 App 不能执行 `scripts/` 脚本，也没有可下载的 `assets/` 二进制资源，因此不要设计脚本或二进制模板。
- 需要模板、规则、示例时，全部写进 `references/*.md`，由 AI 在使用时用 `file_read` 按需读取。
- 技能保存在本地数据库；内置技能被修改时会自动克隆为用户副本。
- 导出 zip 时主文件固定为 `skill.md`，`references/` 一并打包；导入旧包里的 `main.md` 仍可识别，但不要主动生成 main.md。

## 什么是技能？

技能是一组指导 AI 完成特定任务的指令集合，在主文件 `skill.md` 中定义：

```
skill-name/
├── skill.md          (必需 — YAML frontmatter + Markdown 指令)
└── references/       (可选 — 按需读取的 .md / .txt 文档)
```

## 技能的三级加载机制

技能采用渐进式加载，避免撑爆上下文：

1. **元数据**（name + description）——始终可见，约 100 字
2. **skill.md 正文**——调用 `skill_lookup` 后加载（建议控制在 500 行以内）
3. **references/ 配套资源**——AI 用 `file_read` 按需读取

配套资源不会自动内联，AI 只能看到文件名。因此 skill.md 正文必须写明每个资源文件的路径和读取时机。

## 创建技能的流程

### Step 1: 了解用户需求

如果用户说“把这个流程变成技能”，先从对话历史中提取步骤、工具使用和修正点。

向用户了解：
1. 这个技能要帮 AI 完成什么任务？
2. 什么情况下应该触发？（用户会说什么话、提到什么关键词）
3. 期望的输出格式是什么？
4. 有没有特殊的规则或约束？

### Step 2: 规划技能结构

- **skill.md** — 核心工作流程和指令（控制在 500 行以内）
- **references/** — AI 按需读取的规则、标准、领域资料、模板（只放 .md / .txt）

**领域变体模式**：当技能支持多个领域时，按变体组织：
```
cloud-deploy/
├── skill.md          (工作流 + 领域选择)
└── references/
    ├── aws.md
    ├── gcp.md
    └── azure.md
```
AI 只需读取相关的 reference，不用全部加载。

### Step 3: 编写 skill.md

基本结构：

```markdown
---
name: skill-name
description: 技能描述，说明何时使用。要覆盖触发场景和关键词，宁滥勿缺。
---

# 技能标题

说明这个技能做什么、为什么这样工作。

## 工作流程

### Step 1: [步骤名称]
[说明这一步做什么，解释为什么重要]

### Step 2: [步骤名称]
...

## 输出格式
[明确的输出模板]

## 重要规则
- [规则和原因]

## References
- **[references/xxx.md](references/xxx.md)** — 说明什么时候需要读这个文件
```

**编写要点：**

- **名称**：英文小写 + 连字符，如 `novel-reviewer`、`chapter-outliner`
- **描述要贪婪**：AI 容易欠触发，描述要主动覆盖相关场景和同义词。好例子：`当用户想要生成周报、写工作汇报、整理本周进度、总结项目进展时使用此技能，即使用户只说"帮我写个报告"也应触发。`
- **解释原因**：说明为什么这样做，比堆砌 MUST/ALWAYS/NEVER 更有效
- **给示例**：示例比抽象描述更有帮助
- **用祈使句**：直接说“读取文件”、“输出结果”
- **适配本 App**：只调用上节列出的工具；不要假设能执行脚本、访问网络或读写书稿以外的路径

**配套资源引用规范（重要）：**

在 skill.md 正文中必须写明每个配套文件的路径和使用时机：
```markdown
生成报告前，读取 `references/报告规范.md` 检查结构与措辞。
```

并在末尾加 References 索引区块：
```markdown
## References
- **[references/报告规范.md](references/报告规范.md)** — 输出审核标准，在生成最终内容前读取
```

### Step 4: 自我验证

写完技能后，在脑中模拟执行，逐项检查：

1. **触发检查**：选一个最典型的用户说法，技能描述能匹配上吗？
2. **流程完整性**：按步骤走一遍，输出符合预期吗？有没有遗漏步骤？
3. **工具合法性**：所有工具名与参数是否都在上节清单内？是否存在不可执行的脚本步骤？
4. **配套资源引用检查**：每个 references 文件在正文里是否都写了路径和读取时机？末尾有没有 References 索引？
5. **边缘场景**：用户没明确提“技能”时，技能能触发吗？
6. **中断友好**：流程是否能在任意一步被停止按钮中断而不留下错误状态？

发现问题先修正，再交给用户确认。

### Step 5: 保存技能

使用 question 工具询问用户是否确认保存，展示技能概要：
- 技能名称和描述
- skill.md 的核心流程
- 包含的配套文件列表

用户确认后，使用 **create_skill** 工具保存：

```
工具名: create_skill
参数:
{
  "name": "my-skill",
  "description": "技能描述，说明何时使用",
  "content": "完整的 skill.md 内容，包括 frontmatter (---) 和正文",
  "references": {
    "references/规则.md": "规则文件的完整内容",
    "references/模板.md": "模板文件的完整内容"
  }
}
```

**注意**：
- content 必须以 `---` 开头
- references 可省略；键建议写成 `references/文件名.md`
- 不要传入 scripts/、assets/ 下的文件，本 App 不会执行或使用它们

## 修改现有技能

1. 用 **skill_lookup** 读取当前技能的完整内容
2. 了解用户想改什么
3. 提出修改建议，用户确认后用 **update_skill** 更新

```
工具名: update_skill
参数:
{
  "skill_name": "my-skill（与技能列表显示一致）",
  "content": "新的完整 skill.md 内容（包括 frontmatter 和正文）"
}
```

只更新某个配套文件：
```json
{
  "skill_name": "my-skill",
  "reference_name": "references/规则.md",
  "reference_content": "规则的新内容"
}
```

内置技能修改时会自动克隆为用户副本。

## References

- **[references/templates.md](references/templates.md)** — skill.md 模板和示例，创建新技能时参考
