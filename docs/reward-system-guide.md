# Locks Continue 撬锁奖励机制程序指南

## 1. 文档目标

本文定义 Locks Continue 的撬锁表现评价、失败阈值、奖励档位、战利品生成、数据包格式、服务端状态、兼容行为和验收标准。

本文是一份实现规范。代码实现应以本文的边界语义为准；当前工程中尚未存在的字段和类，需要按本文逐步加入。

首期范围：

- 使用模组内置 Java 逻辑完成服务端验证和奖励发放。
- 使用原生数据包定义失败阈值、奖励档位和额外战利品表。
- 支持原版容器、大箱子、桶、潜影盒以及已经接入的 Lootr 容器。
- 不实现 KubeJS API，但数据结构不能阻碍未来扩展。

## 2. 已确定规则

1. 弹子锁、圆盘锁和记忆锁按照本次有效尝试中的失误次数评价表现。
2. 管道锁按照完成时间评价表现，不按照旋转次数或错误方向评价。
3. 管道锁只要起点能够沿双方接口一致的管道到达全部目标终点即可成功；普通分支、空格和无关死路默认不参与成功或失败判定。
4. 数据包可以定义多少次失误或多少 tick 对应一个奖励档位。
5. 数据包可以定义多少次失误或多少 tick 使本次游戏失败。
6. `failure_at = -1` 表示该评价指标不会导致游戏失败。
7. `-1` 只关闭评价指标造成的失败；工具损坏、锁被移除、玩家离开范围、换维度、换工具和会话失效仍然可以结束会话。
8. 奖励可以增加原容器来源战利品表的完整生成轮数，也可以追加独立的奖励战利品表。
9. 所有指标、失败和奖励档位都由服务端计算。客户端不能提交时间、失误总数、奖励档位或“我应该获得奖励”的声明。
10. 同一个锁的奖励配置生成后固定。重新打开 GUI 或执行 `/reload` 不能刷新已经生成锁的奖励规则。
11. 同一个锁只允许结算一次成功奖励。

## 3. 核心术语

| 名称 | 含义 |
| --- | --- |
| `source_loot_table` | 容器原本携带的来源战利品表，例如 `minecraft:chests/ancient_city` |
| `reward_profile` | 数据包中的一套奖励规则资源 ID |
| `metric` | 评价方式，首期为 `mistakes_used` 或 `time_ticks` |
| `metric_value` | 当前有效尝试已经使用的失误数或时间 |
| `failure_at` | 达到该值时本次游戏失败；`-1` 关闭该奖励规则自身的失败阈值 |
| `tier` | 一个奖励档位 |
| `max` | 该奖励档位允许的最大指标值，包含边界值 |
| `source_rolls` | 原来源战利品表总共生成的完整轮数 |
| `source_luck_bonus` | 只加到额外来源表轮次上的 Minecraft 幸运值 |
| `bonus_loot_table` | 可选的额外奖励战利品表 |
| `bonus_rolls` | 额外奖励战利品表生成的轮数 |
| `reward_claimed` | 成功奖励是否已经结算，用于幂等保护 |

`source_rolls` 不是修改原版战利品表某个 pool 内部的 `rolls` 表达式。它表示服务端对整个来源战利品表执行多少次完整生成。

例如：

```text
source_rolls = 1  原表正常生成一轮
source_rolls = 2  原表正常生成一轮，再额外生成一轮
source_rolls = 3  原表总共生成三轮
```

额外来源表轮次使用 `source_luck_bonus` 把额外幸运值传入战利品表。它不修改已经生成的原始战利品；带正 `quality` 的战利品项会得到更高权重，显式使用固定随机条件（例如 `random_chance`）的概率保持不变。默认值在 `source_rolls > 1` 时为 `2.0`，否则为 `0`；显式设为 `0` 可以关闭加成，合法范围为 `0～20`。原有已保存档位若缺少该字段，也会按此默认规则补齐。

## 4. 四种小游戏的评价方式

| 锁类型 | `metric` | 指标增加时机 | 默认评价方向 |
| --- | --- | --- | --- |
| `lockscontinue:pin` | `mistakes_used` | 服务端返回有效 `MISTAKE` | 越少越好 |
| `lockscontinue:rotary` | `mistakes_used` | 压力或错误尝试被服务端判定为有效失败 | 越少越好 |
| `lockscontinue:memory` | `mistakes_used` | 服务端确认一次错误配对 | 越少越好 |
| `lockscontinue:pipe` | `time_ticks` | 第一次合法旋转后，每个服务端 tick 增加 | 越少越好 |

以下情况不得增加 `mistakes_used`：

- 重复网络包。
- 操作序号过期。
- 玩家点击不可旋转格子。
- 客户端与服务端状态不同步。
- 玩家已经不在目标容器附近。
- 会话已经完成或失效。
- 任何返回 `REJECTED` 的操作。

管道锁的时间从第一次被服务端接受的 `PIPE_ROTATE` 开始，而不是从 GUI 打开时开始。这样玩家阅读界面或调整窗口不会损失奖励时间。

## 5. 失败语义

### 5.1 失误型小游戏

每次服务端谜题返回 `MISTAKE`：

1. `mistakes_used += 1`。
2. 应用本次失误对应的工具耐久损耗。
3. 如果 `failure_at >= 0` 且 `mistakes_used >= failure_at`，本次游戏失败并关闭界面。
4. 如果尚未达到失败阈值，游戏继续。

例子：

```json
{
  "metric": "mistakes_used",
  "failure_at": 4
}
```

允许成功时的失误值为 `0`、`1`、`2`、`3`。第 4 次有效失误发生时，本次游戏失败。

### 5.2 时间型小游戏

管道锁第一次合法旋转时记录：

```text
metric_started = true
started_at = serverLevel.getGameTime()
```

当前时间值为：

```text
elapsed_ticks = serverLevel.getGameTime() - started_at
```

管道锁同时受难度中的 `pipe.time_limit_ticks` 和时间型奖励规则中的 `failure_at` 约束，取两者中较短的有效值作为实际时间上限。难度限时默认 `1200` tick（60 秒），允许 `20～7200`；两项各自可用 `-1` 关闭，只有两项都关闭才不限时。若奖励指标为 `mistakes_used`，其 `failure_at` 仍表示失误数，难度的管道限时独立生效。

当 `elapsed_ticks >= 实际时间上限` 时，管道游戏失败。时间失败只应用一次失败耐久损耗，不能在后续服务器 tick 中重复扣除。

活动会话必须由服务端 tick 检查时间阈值，不能只在下一次玩家点击时检查，否则玩家停止操作后计时器永远不会失败。

### 5.3 无限失败阈值

```json
{
  "failure_at": -1
}
```

表示：

- 该奖励规则不再因自己的评价指标导致失败。对于管道锁，难度中的 `pipe.time_limit_ticks` 仍会独立限制时间；若要无限延长，难度限时也必须设为 `-1`。
- 奖励档位仍然根据最终指标值选择。
- 工具仍然可以因为每次有效失误的耐久损耗而损坏。
- 无效会话仍然会被服务端拒绝。

## 6. 奖励档位匹配

奖励档位按 `max` 从小到大排序，并选择第一个满足以下条件的档位：

```text
metric_value <= tier.max
```

`max` 是包含边界的。

示例：

```json
{
  "tiers": [
    { "max": 0, "source_rolls": 3 },
    { "max": 1, "source_rolls": 2 },
    { "max": 3, "source_rolls": 1 }
  ]
}
```

对应行为：

| 指标值 | 选择档位 |
| ---: | --- |
| 0 | `max = 0` |
| 1 | `max = 1` |
| 2～3 | `max = 3` |

如果玩家成功时没有任何档位能够匹配，但尚未触发失败，则使用安全回退：

```text
source_rolls = 1
bonus_rolls = 0
```

不得自动重复最后一个高奖励档位，以免 `failure_at = -1` 时无限失误仍获得额外奖励。

## 7. 数据包目录

建议增加：

```text
data/<namespace>/lockscontinue/rewards/*.json
```

现有目录保持：

```text
data/<namespace>/lockscontinue/difficulties/*.json
data/<namespace>/lockscontinue/affixes/*.json
data/<namespace>/lockscontinue/lockpicks/*.json
data/<namespace>/lockscontinue/loot_rules/*.json
```

`loot_lock_rules` 可以继续作为旧别名兼容，但新文件和文档统一使用 `loot_rules`。

资源路径示例：

```text
data/lockscontinue/lockscontinue/rewards/standard.json
```

对应资源 ID：

```text
lockscontinue:standard
```

## 8. 奖励配置完整格式

一份奖励配置可以根据锁类型和难度选择不同规则。规则按文件中的排列顺序匹配，使用第一条成功匹配的规则。

```json
{
  "schema": 1,
  "rules": [
    {
      "match": {
        "lock_types": [
          "lockscontinue:pin",
          "lockscontinue:rotary",
          "lockscontinue:memory"
        ]
      },
      "metric": "mistakes_used",
      "failure_at": 4,
      "tiers": [
        {
          "max": 0,
          "source_rolls": 3,
          "source_luck_bonus": 2.0,
          "bonus_loot_table": "lockscontinue:bonus/perfect",
          "bonus_rolls": 2
        },
        {
          "max": 1,
          "source_rolls": 2,
          "source_luck_bonus": 2.0,
          "bonus_loot_table": "lockscontinue:bonus/good",
          "bonus_rolls": 1
        },
        {
          "max": 3,
          "source_rolls": 1,
          "bonus_rolls": 0
        }
      ]
    },
    {
      "match": {
        "lock_types": ["lockscontinue:pipe"],
        "difficulties": [
          "lockscontinue:simple",
          "lockscontinue:normal"
        ]
      },
      "metric": "time_ticks",
      "failure_at": 600,
      "tiers": [
        {
          "max": 120,
          "source_rolls": 3,
          "bonus_loot_table": "lockscontinue:bonus/fast",
          "bonus_rolls": 2
        },
        {
          "max": 240,
          "source_rolls": 2,
          "bonus_loot_table": "lockscontinue:bonus/good",
          "bonus_rolls": 1
        },
        {
          "max": 599,
          "source_rolls": 1,
          "bonus_rolls": 0
        }
      ]
    },
    {
      "match": {
        "lock_types": ["lockscontinue:pipe"],
        "difficulties": [
          "lockscontinue:hard",
          "lockscontinue:expert",
          "lockscontinue:master"
        ]
      },
      "metric": "time_ticks",
      "failure_at": 1200,
      "tiers": [
        { "max": 300, "source_rolls": 3 },
        { "max": 600, "source_rolls": 2 },
        { "max": 1199, "source_rolls": 1 }
      ]
    }
  ]
}
```

上述时间值只是格式示例，不是最终平衡值。最终默认值需要通过实际游戏测试确定。

### 8.1 `match`

支持字段：

```json
{
  "lock_types": ["lockscontinue:pipe"],
  "difficulties": ["lockscontinue:master"]
}
```

- 省略 `lock_types` 表示匹配所有锁类型。
- 省略 `difficulties` 表示匹配所有难度。
- 数组内使用资源 ID，不使用显示名称。
- 第一条匹配规则生效，因此更具体的规则应写在通用规则之前。

### 8.2 `metric`

首期只接受：

```text
mistakes_used
time_ticks
```

无效值导致当前奖励配置文件加载失败，但不能导致服务器崩溃。

### 8.3 `failure_at`

- `-1`：关闭该奖励规则自身的失败阈值；不关闭难度中的管道限时。
- 失误型规则：允许 `1～100000`。
- 时间型规则：允许 `1～72000`，即最多 1 小时游戏 tick。
- `0` 无意义，应视为配置错误。

### 8.4 `source_rolls`

- 默认值为 `1`。
- 建议允许范围为 `1～5`。
- 表示原来源战利品表的总生成轮数，不是额外轮数。
- 容器没有 `source_loot_table` 时，该字段退化为 `1`，不能复制容器中已经存在的物品。

### 8.5 `source_luck_bonus`

- 默认值：当 `source_rolls > 1` 时为 `2.0`，否则为 `0`。
- 合法范围为 `0～20`；显式 `0` 可关闭额外轮次的幸运加成。
- 只用于 `source_rolls - 1` 轮额外来源表生成，不影响容器原本的首轮战利品或单独的 `bonus_loot_table`。
- 该值通过 Minecraft 的 Luck 参数提高带正 `quality` 战利品项的权重；直接使用固定随机概率条件的条目不受影响。

### 8.6 `bonus_loot_table`

- 可选资源 ID。
- 仅当 `bonus_rolls > 0` 时需要。
- 找不到该战利品表时跳过额外奖励并记录包含资源路径的错误日志。
- 缺失奖励表不得阻止原容器解锁。

### 8.7 `bonus_rolls`

- 默认值为 `0`。
- 建议允许范围为 `0～5`。
- 大于 `0` 时必须提供 `bonus_loot_table`。

## 9. 战利品规则如何引用奖励配置

现有 `loot_rules` 增加可选字段：

```json
{
  "priority": 100,
  "match": {
    "loot_tables": ["minecraft:chests/ancient_city"]
  },
  "lock_chance": 0.9,
  "lock_types": {
    "lockscontinue:rotary": 3,
    "lockscontinue:memory": 1
  },
  "difficulties": {
    "lockscontinue:hard": 3,
    "lockscontinue:master": 1
  },
  "reward_profile": "lockscontinue:ancient_city"
}
```

奖励配置解析顺序：

1. 命中的 `loot_rules` 明确指定 `reward_profile`。
2. 没有指定时使用 `lockscontinue:default`。
3. 找不到指定配置时记录错误并使用安全默认规则。

安全默认规则：

```text
metric          按锁类型选择：管道为 time_ticks，其他为 mistakes_used
failure_at      沿用现有难度快照中的失败限制；管道为 -1
source_rolls    1
bonus_rolls     0
```

安全默认奖励中的管道 `failure_at: -1` 不会关闭难度的 `pipe.time_limit_ticks`。

## 10. 持久化数据模型

### 10.1 锁实例

`LockInstance` 升级到 schema 2，并保存已经解析完成的奖励快照：

```java
record LockInstance(
    int schemaVersion,
    ResourceLocation lockType,
    ResourceLocation difficulty,
    long seed,
    ResourceLocation sourceLootTable,
    List<ResourceLocation> affixes,
    ResourceLocation rewardProfile,
    ResolvedRewardSnapshot rewardSnapshot
) {}
```

NBT 示例：

```snbt
lockscontinue_lock: {
  schema: 2,
  type: "lockscontinue:pipe",
  difficulty: "lockscontinue:hard",
  seed: 123456L,
  source_loot_table: "minecraft:chests/ancient_city",
  affixes: [],
  reward_profile: "lockscontinue:ancient_city",
  reward: {
    schema: 1,
    metric: "time_ticks",
    failure_at: 1200,
    tiers: [
      {max: 300, source_rolls: 3, bonus_rolls: 0},
      {max: 600, source_rolls: 2, bonus_rolls: 0},
      {max: 1199, source_rolls: 1, bonus_rolls: 0}
    ]
  }
}
```

保存完整的已解析规则，而不是只保存 `reward_profile`，可以保证 `/reload` 只影响新生成的锁。

### 10.2 奖励尝试状态

需要持久化本次尝试的表现，避免玩家在发生失误后关闭并重开 GUI 来清零失误，或者重开管道锁重置计时器。

建议状态：

```java
record RewardAttemptState(
    UUID playerId,
    boolean metricStarted,
    long startedAt,
    int mistakesUsed,
    boolean failed
) {}
```

状态应与锁实例一起保存，或保存在按维度、容器位置和玩家 UUID 索引的 `SavedData` 中。

规则：

- 同一玩家重新打开同一个锁时继续原尝试。
- 管道计时开始后，关闭 GUI 不会暂停或重置计时。
- 失误次数不会因为客户端关闭界面而清零。
- 达到失败阈值后结束当前尝试；下一次重新开锁创建新的尝试。
- 其他玩家可以拥有独立尝试状态，但第一个成功者会解锁容器并使其他会话失效。
- 大箱子两半共享同一份尝试状态。

如果首期暂时不实现持久尝试状态，则必须明确把“关闭 GUI”视为当前尝试失败。不能静默重置指标，否则奖励可以被无限刷到完美档位。

## 11. Java 类型设计

建议增加：

```java
enum RewardMetric {
    MISTAKES_USED,
    TIME_TICKS
}

record RewardTier(
    int max,
    int sourceRolls,
    ResourceLocation bonusLootTable,
    int bonusRolls,
    double sourceLuckBonus
) {}

record RewardRule(
    Set<ResourceLocation> lockTypes,
    Set<ResourceLocation> difficulties,
    RewardMetric metric,
    int failureAt,
    List<RewardTier> tiers
) {}

record RewardProfileDefinition(
    ResourceLocation id,
    List<RewardRule> rules
) {}

record ResolvedRewardSnapshot(
    ResourceLocation profileId,
    RewardMetric metric,
    int failureAt,
    List<RewardTier> tiers
) {}
```

建议职责划分：

| 类 | 职责 |
| --- | --- |
| `LockDefinitionManager` | 加载 `rewards`，校验引用，在创建锁时解析规则 |
| `RewardProfileDefinition` | 表示数据包中的可重载配置 |
| `ResolvedRewardSnapshot` | 保存进锁实例和会话的不可变规则 |
| `RewardAttemptState` | 保存玩家当前锁的时间或失误表现 |
| `LockRewardResolver` | 根据锁类型、难度和配置选择具体规则及奖励档位 |
| `LockRewardDistributor` | 幂等地生成来源表额外轮次和奖励表 |
| `LootrRewardBridge` | 在安装 Lootr 时向玩家独立容器发放奖励 |

## 12. 会话字段

`LockSessionManager.Session` 至少增加：

```java
private final ResolvedRewardSnapshot reward;
private RewardAttemptState rewardAttempt;
private boolean rewardClaimed;
```

不要使用客户端界面 tick 计算时间。管道时间必须来自服务端 `ServerLevel#getGameTime()`。

失误值建议显式保存 `mistakesUsed`，不要只通过 `initialMistakes - mistakesLeft` 推导，因为 `failure_at = -1` 时可能没有有限的 `mistakesLeft`。

## 13. 服务端完整流程

### 13.1 生成锁

```text
容器获得来源战利品表
        ↓
匹配 LootLockRule
        ↓
选择锁类型、难度和词条
        ↓
选择 reward_profile
        ↓
根据锁类型和难度解析 RewardRule
        ↓
生成 ResolvedRewardSnapshot
        ↓
与 LockInstance 一起持久化
```

大箱子只执行一次随机和奖励规则解析，两半保存相同锁实例。

### 13.2 开始会话

1. 验证玩家、工具、距离、维度、区块和锁实例。
2. 读取锁中固定的 `ResolvedRewardSnapshot`。
3. 读取或创建该玩家的 `RewardAttemptState`。
4. 构造服务端谜题。
5. 只向客户端发送显示需要的指标类型、当前值和失败阈值。

### 13.3 处理操作

```text
收到操作包
   ↓
验证会话与操作序号
   ↓
管道游戏是否已经达到实际时间上限？── 是 → TIME_LIMIT 失败
   ↓ 否
服务端谜题处理操作
   ├─ REJECTED → 不改变指标、不损耗工具
   ├─ UPDATED/PROGRESS → 保存谜题状态
   ├─ MISTAKE → mistakes_used + 1，并检查失败阈值
   └─ SOLVED → 进入奖励结算
```

管道锁第一次合法 `PIPE_ROTATE` 确认后，以该操作所在的服务端 tick 记录开始时间。达到时间边界时使用 `elapsed_ticks >= 实际时间上限` 判定失败；奖励时间指标和游戏倒计时共用这个持久化开始时间。

### 13.4 成功结算

成功时必须按以下顺序执行：

1. 再次验证锁、玩家、工具和容器组。
2. 确认 `reward_claimed == false`。
3. 计算最终 `metric_value`。
4. 选择奖励档位；无匹配时使用安全默认档位。
5. 将奖励结算标记为处理中，防止重入。
6. 确保原容器基础战利品只生成一次。
7. 生成 `source_rolls - 1` 轮额外来源战利品。
8. 生成 `bonus_rolls` 轮 `bonus_loot_table`。
9. 标记 `reward_claimed = true`。
10. 清除容器锁。
11. 发送成功结果、播放声音并正常打开容器。

奖励发放和解锁需要构成一个不可重复的服务端操作。重复成功包、菜单重开和客户端重发不得再次生成奖励。

### 13.5 游戏失败

失败时：

1. 不生成任何奖励。
2. 容器保持上锁。
3. 播放对应失败反馈。
4. 非管道游戏不得对最后一次失误重复扣除两次耐久。
5. 管道时间失败只扣除一次配置的失败耐久。
6. 清理当前会话，并将持久尝试标记为失败。
7. 玩家下次尝试时创建新的表现记录。

## 14. 战利品生成要求

### 14.1 原来源战利品表

推荐流程：

1. 先让容器的原版基础战利品正常展开一次。
2. 使用 `source_loot_table` 和服务器构造的 `LootParams` 生成额外轮次。
3. 每轮使用由锁种子派生的不同确定性种子。
4. 将生成结果放入临时列表后再安全合并进目标容器。

派生种子示意：

```text
rollSeed = mix(lock.seed, rewardTierIndex, rollIndex, lootTableId)
```

不能对所有额外轮次重复使用同一个种子，否则可能生成完全相同的奖励。

### 14.2 额外奖励表

`bonus_loot_table` 使用同一个解锁玩家和容器上下文，但使用独立派生种子。奖励表不应再次触发锁生成逻辑。

### 14.3 容器空间不足

首期统一策略：

- 不覆盖容器中已经存在的物品。
- 优先合并相同物品堆。
- 再放入空槽位。
- 剩余物品在容器中心掉落，并设置正常拾取延迟。
- 不允许因为容器已满而静默删除高档奖励。

### 14.4 手动上锁容器

手动上锁的容器通常没有 `source_loot_table`：

- `source_rolls` 不复制现有物品。
- `source_rolls` 按 `1` 处理。
- 如果配置了 `bonus_loot_table`，仍然可以获得额外奖励。
- 如果不希望手动锁产生奖励，工具箱安装锁时应选择无奖励的默认配置。

## 15. 容器兼容

### 15.1 普通单容器

箱子、桶和潜影盒在解锁前展开基础战利品并追加奖励，然后打开菜单。

### 15.2 大箱子

- 使用规范化的容器组位置作为奖励键。
- 两半只允许一次奖励结算。
- `source_rolls` 和 `bonus_rolls` 面向组合容器，不是每半各执行一次。
- 奖励幂等标记同步到两半。

### 15.3 Lootr

Lootr 的战利品通常按玩家独立生成，不能直接把额外物品写入共享方块实体槽位。

要求：

- 未安装 Lootr 时不能出现类加载错误。
- 通过可选兼容层向完成开锁的玩家对应 Lootr 库存追加奖励。
- `source_rolls` 使用 `LootTable` 或 `specialLootChest_table` 识别出的来源表。
- 奖励领取标记按玩家保存，防止同一玩家重复领取。
- 其他玩家后续打开已经解锁的 Lootr 容器时，只获得其正常 Lootr 战利品，除非数据包明确设计为每个玩家都可进行独立开锁。

首期默认采用“完成开锁的玩家获得表现奖励”规则。

## 16. 数据包加载与校验

加载 `rewards/*.json` 时至少检查：

- `schema` 是否受支持。
- `rules` 是否为非空数组。
- `metric` 是否为已知值。
- `failure_at` 是否为 `-1` 或合法正数。
- `tiers` 是否按 `max` 严格递增。
- `max` 是否为非负数。
- 有限失败阈值下，奖励档位的 `max` 是否小于 `failure_at`。
- `source_rolls` 是否在 `1～5`。
- `bonus_rolls` 是否在 `0～5`。
- `bonus_rolls > 0` 时是否存在 `bonus_loot_table`。
- 锁类型、难度、奖励配置和战利品表资源 ID 是否合法。
- `loot_rules` 引用的 `reward_profile` 是否存在。

错误处理：

- 单个错误文件不能阻止服务器启动或 `/reload`。
- 日志必须包含完整资源路径和具体字段。
- 错误配置使用安全默认规则，不允许退化成免费多轮来源战利品。
- 同资源 ID 按数据包优先级覆盖。

## 17. 网络协议与客户端显示

网络协议需要增加或升级：

```text
rewardMetric
metricValue
failureAt
metricStarted
pipeTimeLeftTicks
```

`OpenLockPacket` 与 `LockResultPacket` 都携带服务端计算的 `pipeTimeLeftTicks`：`-1` 表示不限时，`0` 表示时间耗尽，正数为实际剩余 tick。客户端使用该值显示倒计时，不能仅从奖励规则的 `failure_at` 推断管道限时。

客户端只需要知道状态栏显示数据，不需要知道服务器将生成哪个战利品表。

建议状态栏显示：

```text
弹子锁：失误 1 / 4
管道锁：用时 8.6s / 30.0s
无限模式：失误 7 / ∞
```

客户端可以根据公开档位显示“完美、良好、普通”等质量文字，但最终档位仍由服务端计算。

以下内容不得由客户端发送：

- 最终耗时。
- 总失误数。
- 奖励档位。
- 来源战利品生成次数。
- 奖励战利品表 ID。
- 奖励已经领取的声明。

协议字段改变后必须提升网络协议版本，避免新旧客户端误连接。

## 18. 旧数据迁移

### 18.1 schema 1 锁实例

旧锁没有奖励字段。首次读取时：

1. 使用其已有 `lockType`、`difficulty` 和 `sourceLootTable`。
2. 根据当前 `loot_rules` 解析一次奖励配置。
3. 生成 `ResolvedRewardSnapshot`。
4. 升级为 schema 2 并保存。

迁移完成后，后续 `/reload` 不改变该锁奖励。

### 18.2 旧数据包

未提供 `reward_profile` 的旧 `loot_rules` 使用 `lockscontinue:default`。原本的锁生成概率、类型、难度和词条选择不得改变。

旧难度中的 `failure.mistakes` 和 `memory.mismatch_limit` 作为没有奖励配置时的回退。奖励规则存在时，失误型游戏以奖励规则的 `failure_at` 为失败阈值。管道锁的 `pipe.time_limit_ticks` 则独立生效，与时间型奖励规则的正 `failure_at` 取较短值；旧数据包未提供难度限时的，使用默认 `1200` tick。

## 19. 安全与防刷要求

- 奖励只能在服务端确认 `SOLVED` 后发放。
- 解锁前再次确认容器仍然是同一个锁实例。
- 使用原子式或等价幂等流程设置 `reward_claimed`。
- 重复包不得重复发奖。
- 关闭 GUI 不得重置表现指标。
- 断线后重新登录不得刷新表现指标。
- 换工具、换维度和离开范围使当前会话失效，但不能把已有失误或已开始的时间偷偷清零。
- 数据包最多允许有限的 `source_rolls` 和 `bonus_rolls`。
- 缺失战利品表、满容器和兼容模组异常不得导致锁被重复结算。
- 额外生成的战利品不能再次触发本模组的锁生成事件。

## 20. 实施顺序

1. 增加 `RewardMetric`、`RewardTier`、`RewardRule` 和 `RewardProfileDefinition`。
2. 扩展 `LockDefinitionManager`，加载和校验 `rewards/*.json`。
3. 为 `LootLockRule` 增加 `reward_profile`。
4. 增加 `ResolvedRewardSnapshot` 的 NBT 读写。
5. 将 `LockInstance` 升级到 schema 2，并完成旧锁迁移。
6. 增加 `RewardAttemptState`，确保关闭和重开 GUI 不刷新表现。
7. 将失误型小游戏的 `MISTAKE` 接入 `mistakes_used`。
8. 为管道游戏增加服务端首次操作计时和活动会话超时判定。
9. 增加统一的奖励档位选择器。
10. 实现原版容器 `LockRewardDistributor`。
11. 实现大箱子一次性结算。
12. 实现 Lootr 可选兼容层。
13. 升级网络协议和状态栏显示。
14. 添加默认奖励数据包和语言文本。
15. 完成单元测试、集成测试和实际游戏验收。

## 21. 测试用例

### 21.1 配置解析

- 合法的失误配置可以加载。
- 合法的时间配置可以加载。
- `failure_at = -1` 可以加载。
- `failure_at = 0` 被拒绝。
- 重复或乱序 `max` 被拒绝。
- 超出限制的 `source_rolls` 和 `bonus_rolls` 被拒绝。
- 缺失奖励表不会使服务器崩溃。
- 数据包覆盖同 ID 时使用高优先级版本。

### 21.2 档位选择

- 指标等于 `max` 时命中该档位。
- 指标位于两个档位之间时命中后一个档位。
- 没有档位匹配时使用安全默认奖励。
- 达到有限 `failure_at` 时不能获得奖励。
- 无限失败模式下，大指标值不会自动获得最后一个高奖励档位。

### 21.3 失误游戏

- Pin 的有效 timing mistake 增加一次。
- Rotary 的有效失败增加一次。
- Memory 的 mismatch 增加一次。
- `REJECTED` 操作不增加失误。
- 最后一次失误只扣一次耐久。
- 关闭并重开 GUI 后失误数不清零。

### 21.4 管道游戏

- 打开 GUI 不开始计时。
- 第一次合法旋转开始计时。
- 点击起点、终点或空格不错误启动计时。
- 普通分支和无关死路不造成失败。
- 起点到全部终点连通后成功。
- 达到时间阈值时只失败一次。
- 关闭并重开 GUI 后计时继续。
- 奖励 `failure_at = -1`、难度限时为正数时，难度限时仍会触发失败。
- 奖励时间阈值与难度限时都为正数时，取较短值。
- 奖励 `failure_at = -1` 且难度 `time_limit_ticks = -1` 时不会因为时间失败。
- 奖励按失误数评价时，难度限时及客户端倒计时仍然生效。

### 21.5 战利品结算

- `source_rolls = 1` 与原版行为一致。
- `source_rolls = 3` 只额外生成两轮来源表。
- `source_luck_bonus` 只影响额外来源表轮次，基础战利品保持原概率。
- 没有 `quality` 权重的直接随机概率条目不因 `source_luck_bonus` 改变。
- `bonus_rolls` 按次数生成额外表。
- 不同轮次使用不同确定性种子。
- 重复成功包只发一次奖励。
- 容器满时奖励不会覆盖原物品。
- 手动上锁容器不会复制已有物品。
- 大箱子不会在两半各发一次。
- Lootr 同一玩家不会重复领取。
- 未安装 Lootr 时启动和运行正常。

### 21.6 迁移与重载

- schema 1 锁升级后可以正常开启。
- `/reload` 改变新生成锁的奖励规则。
- `/reload` 不改变已有 schema 2 锁的奖励快照。
- 移除奖励数据包后，已有锁仍使用其已保存规则。
- 已保存奖励表 ID 失效时安全跳过额外奖励并完成解锁。

## 22. 验收标准

实现完成必须满足：

- 三种失误型小游戏按照服务端有效失误数选择奖励。
- 管道锁按照服务端时间选择奖励。
- 数据包可以分别定义奖励档位和失败阈值。
- `failure_at = -1` 的含义稳定且不会禁用其他会话安全验证。
- 原来源战利品表可以按 `source_rolls` 生成多轮。
- 独立奖励表可以按 `bonus_rolls` 生成多轮。
- 失败不会发奖，成功不会重复发奖。
- 关闭 GUI、断线和重开不能刷新表现指标。
- 大箱子只结算一次。
- 原版容器和 Lootr 容器行为明确且经过验证。
- 旧锁和旧数据包可以安全迁移。
- 无效数据包不会导致服务器崩溃或意外增加奖励。

## 23. 首期非目标

- KubeJS API。
- 客户端决定奖励。
- 直接编辑或重写其他模组的战利品表 JSON。
- 根据 GUI 帧率计算管道时间。
- 使用旋转次数作为管道默认奖励指标。
- 将已有容器物品复制为奖励。
- 无限 `source_rolls` 或 `bonus_rolls`。
- 自动开锁或跳过小游戏的奖励词条。
