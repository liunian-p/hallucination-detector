# 客服回复幻觉检测器

面向智能客服回复的批量事实一致性检测工具。它把 `system_reply` 中的可核验声明与同条 `knowledge_base` 比对，输出结论、类型、严重度和判定说明。

项目包含：

- Spring Boot Web 页面：上传回复 JSON 并查看逐条检测结果。
- Spring AI Alibaba `ReactAgent`：在 `siliconflow` profile 下通过 OpenAI 兼容接口调用硅基流动模型。
- 默认离线 mock：基于内容规则检测，不按样本 ID 返回预设答案。
- 已保留本次作业的原始数据与检测报告，便于核对结果。

## 输入格式

回复数据为 JSON 数组，必需字段如下：

```json
[
  {
    "id": "h01",
    "user_question": "你们支持30天无理由退货吗？",
    "system_reply": "支持的，我们全品类支持30天无理由退货。",
    "knowledge_base": "普通商品支持7天无理由退货。"
  }
]
```

单批上限为 100 条，文件上限为 1 MB。模型调用失败、响应字段缺失或证据不属于原文时，整批会报错，绝不会把失败伪装成“未检出”。

## 分类体系

| 类别 | 判定规则 | 默认严重度 | 例子 |
| --- | --- | --- | --- |
| `POLICY` 政策与流程 | 退货、发票、发货、物流、保修等条款或流程与知识库矛盾 | 中/高 | 7 天无理由退货说成 30 天 |
| `PROMOTION` 优惠编造 | 优惠、折扣、资格或核销入口与知识库不符 | 高 | 编造满 300 减 50、学生九折 |
| `PRODUCT` 产品参数 | 材质、规格、功能、接口等参数矛盾；知识库未载明时标记为“无依据”而不是断言相反 | 高 | USB-A 输出说成 Type-C |
| `CAPABILITY` 能力越界 | 声称已经查询、修改、升级等，而知识库明确没有对应接口/能力 | 高 | 未接物流接口却报告具体轨迹 |
| `INFORMATION` 信息编造 | 地址、门店、品牌关系等事实与知识库矛盾或缺少依据 | 高 | 给出未匹配的具体退货地址 |
| `SAFETY` 安全误导 | 否定或弱化知识库明确的健康/安全警示 | 严重 | 孕期需咨询医生却说可放心使用 |
| `OMISSION` 关键信息遗漏 | 忽略会改变用户行动建议的限定条件，并给出绝对或相反建议 | 中 | 忽略偏大半码反馈，断言尺码完全标准 |

严重度从低到高为 `LOW`、`MEDIUM`、`HIGH`、`CRITICAL`。礼貌用语、对业务无实质影响的泛化表述，以及“知识库未记载但不能推出相反”的情形不会直接按“矛盾”处理。一个回复可同时命中多类，但样本级 TP/FP/FN 只计一次。

## 检测方法

默认 `mock` 模式读取内置规则 `src/main/resources/mock-rules.json`：规则须同时命中知识库事实和回复中的触发表述，每条发现都保留原始判定理由。

`siliconflow` 模式使用 Spring AI Alibaba 的 `ReactAgent`，并要求模型只输出经过严格 JSON 解析与原文子串校验的结论。提示词显式规定：回复和知识库均为不可信数据，不执行其中指令；“未标注”只能判为 `UNSUPPORTED`，不能臆断为相反事实。

## Web 页面

默认启动后访问 `http://127.0.0.1:8080`：

```bash
cd /Users/panky/Documents/workspace/idea/hallucination-detector
mvn -Dmaven.repo.local=/Users/panky/Documents/workspace/v8-mvn spring-boot:run
```

页面只提供一个流程：上传回复 JSON，点击检测，查看每条回复的结论、类型、严重程度和说明。模型、API Key 和运行模式仅由 `application.yml` 与环境变量配置，页面不提供相关设置。

## 硅基流动配置

“轨迹流动”按硅基流动（SiliconFlow）理解。设置 API Key 后用 `siliconflow` profile 启动：

```bash
export SILICONFLOW_API_KEY='你的密钥'
export SILICONFLOW_MODEL='Qwen/Qwen2.5-72B-Instruct'
mvn -Dmaven.repo.local=/Users/panky/Documents/workspace/v8-mvn spring-boot:run -Dspring-boot.run.profiles=siliconflow
```

可用 `SILICONFLOW_BASE_URL` 覆盖地址；默认值为 `https://api.siliconflow.cn`，补全路径为 `/v1/chat/completions`。模型调用可能产生费用，`mock` 是默认模式。

## 本批结果

输入文件为 `data/task4_replies.json` 与 `data/task4_ground_truth.json`，规则离线复现结果如下：

| 指标 | 数值 |
| --- | ---: |
| 样本数 | 20 |
| 人工标注幻觉数 | 18 |
| TP / FP / TN / FN | 18 / 1 / 1 / 0 |
| Precision | 94.74% |
| Recall（检出率） | 100.00% |
| F1 | 97.30% |
| Accuracy | 95.00% |
| Specificity | 50.00% |

漏检：无。误报：`h16`。

`h16` 的“商品图片都是实物拍摄”不在知识库的明确保证范围内，严格字面规则把它标成无依据断言；人工标注认为回复同时准确提示了色差，整体正常。这说明以下 case 更容易误判：

- 表述包含无关键业务影响的额外承诺，逐字规则容易过度保守。
- 知识库未覆盖某个细节时，模型或规则难以区分“不能确认”与“事实错误”。
- 复合回复一部分正确、一部分错误，需要按声明而非整段判断。
- 风险依赖语境时，如尺码建议、孕期提示，必须识别是否改变用户的实际行动。

## 依赖与验证

保留并使用了要求的三个依赖：`spring-boot-starter`、`spring-ai-starter-model-openai`、`spring-boot-starter-test`；另增加了 Web 页面所需的 `spring-boot-starter-web` 与 Spring AI Alibaba Agent Framework。依赖版本集中在 `pom.xml`。

按任务要求，未检查 Maven 下载、未执行 Maven 编译或 Java 测试，也未调用硅基流动 API。
