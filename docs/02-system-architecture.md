# 智能考研系统系统架构书

## 1. 文档目的

本文描述智能考研系统的总体技术架构、技术栈、功能导图、系统架构图、主要模块关系和 ER 图。内容依据当前项目代码编写，适用于开发、答辩和后续维护。

## 2. 技术栈

| 层次 | 技术 | 当前项目位置 |
| --- | --- | --- |
| 前端框架 | Vue 3.5.12 | `frontend/src/App.vue` |
| 构建工具 | Vite 5.4.10 | `frontend/vite.config.js` |
| 前端图标 | lucide-vue-next | `frontend/package.json` |
| 后端框架 | Spring Boot 3.3.5 | `backend/pom.xml` |
| Web API | Spring MVC | `controller` 包 |
| 安全认证 | Spring Security + JWT | `security`、`config` 包 |
| ORM | Spring Data JPA / Hibernate | `repository`、`model` 包 |
| 数据库 | H2 文件数据库 | `application.yml` |
| 文件抽取 | PDFBox、Apache POI、Tesseract OCR | `TextExtractionService` |
| AI 调用 | Java HttpClient + OpenAI 兼容接口 | `ChatService`、`StudyPlanAiService`、`EmbeddingService` |
| 检索服务 | Elasticsearch，可选 | `ElasticsearchService` |
| 文件存储 | 本地上传目录 `./uploads` | `application.yml` |

## 3. 功能导图

```mermaid
mindmap
  root((智能考研系统))
    账号与安全
      用户注册
      用户登录
      JWT 鉴权
      用户数据隔离
    我的知识库
      我的资料
        创建文件夹
        修改文件夹
        删除文件夹
        子文件夹管理
      上传编辑
        上传 PDF
        上传 Word
        上传图片
        上传文本
        OCR/文本抽取
        编辑抽取文本
        加入/移出知识库
      知识问答
        使用知识库问答
        来源引用
        深度回答
        教师模式
        直接聊天
        会话生成笔记
    学习规划
      自我规划
        周课表
        新增计划
        修改计划
        删除计划
        撤回修改
      AI 规划
        多轮讨论
        生成草稿
        预览操作
        确认写入日程
    错题集
      上传错题
        题干文本
        题目文件
        题目图片
        解析文本
        解析文件
        解析图片
      状态管理
        未掌握
        自定义状态
        完全掌握
      科目标签
      随机练习
      浏览筛选
    AI 设置
      角色定位
      系统提示词
      聊天模型配置
      Embedding 配置
      本地预设
```

## 4. 系统总体架构图

```mermaid
flowchart LR
    U["用户浏览器"] --> FE["Vue + Vite 前端"]
    FE -->|"REST JSON / FormData / SSE"| API["Spring Boot API"]

    API --> AUTH["认证与鉴权模块<br/>Spring Security + JWT"]
    API --> FOLDER["资料文件夹模块"]
    API --> FILE["资料上传与文本抽取模块"]
    API --> CHAT["知识问答模块"]
    API --> PLAN["学习计划模块"]
    API --> MISTAKE["错题模块"]
    API --> SETTING["AI 设置模块"]

    AUTH --> DB[("H2 文件数据库")]
    FOLDER --> DB
    FILE --> DB
    CHAT --> DB
    PLAN --> DB
    MISTAKE --> DB
    SETTING --> DB

    FILE --> UPLOAD["本地 uploads 目录"]
    MISTAKE --> UPLOAD
    FILE --> OCR["Tesseract OCR<br/>本地命令"]
    FILE --> PARSER["PDFBox / Apache POI"]

    CHAT --> ES[("Elasticsearch<br/>可选")]
    FILE --> ES
    CHAT --> LLM["OpenAI 兼容聊天模型<br/>用户自填 API Key"]
    PLAN --> LLM
    CHAT --> EMB["Embedding 模型<br/>用户自填 API Key"]
    ES --> EMB
```

## 5. 后端分层架构

```mermaid
flowchart TB
    Controller["Controller 层<br/>接收请求、参数校验、返回 DTO"]
    Service["Service 层<br/>业务规则、资源归属校验、流程编排"]
    Repository["Repository 层<br/>JPA 数据访问"]
    Model["Model 层<br/>JPA 实体与枚举"]
    External["外部/本地资源<br/>上传目录、OCR、ES、模型 API"]

    Controller --> Service
    Service --> Repository
    Repository --> Model
    Service --> External
```

主要包说明：

| 包 | 职责 |
| --- | --- |
| `controller` | 暴露 REST API，包含认证、文件夹、文件、问答、计划、错题、AI 设置接口 |
| `service` | 实现业务流程和资源归属校验 |
| `repository` | JPA Repository，负责数据库查询 |
| `model` | 用户、资料、错题、计划、AI 配置等实体 |
| `dto` | 请求和响应对象 |
| `security` | JWT 生成、解析、过滤器和认证主体 |
| `config` | Spring Security 配置 |

## 6. 核心业务流程架构

### 6.1 资料入库流程

```mermaid
sequenceDiagram
    participant User as 用户
    participant FE as 前端
    participant API as FileController
    participant FS as FileService
    participant TX as TextExtractionService
    participant DB as H2/JPA
    participant ES as ElasticsearchService

    User->>FE: 选择文件并上传
    FE->>API: POST /api/folders/{folderId}/files
    API->>FS: upload(folderId,userId,tag,file)
    FS->>DB: 校验文件夹归属
    FS->>FS: 保存原始文件到 uploads
    FS->>TX: 按类型抽取文本/OCR
    TX-->>FS: extractedText
    FS->>DB: 保存 StudyFile
    FS->>DB: 删除旧 KnowledgeChunk
    FS->>DB: 按 800 字符切片保存 KnowledgeChunk
    FS-->>FE: 返回文件信息与抽取文本
    FS->>ES: 异步 reindexFile
```

### 6.2 知识问答流程

```mermaid
sequenceDiagram
    participant User as 用户
    participant FE as 前端
    participant API as ChatController
    participant CS as ChatService
    participant ES as ElasticsearchService
    participant DB as H2/JPA
    participant LLM as 聊天模型

    User->>FE: 输入问题
    FE->>API: POST /api/chat 或 /api/chat/stream
    API->>CS: ask/askStream(userId, request)
    CS->>DB: 校验文件夹归属并读取 AI 设置
    CS->>ES: 混合检索候选片段
    alt ES 不可用或无结果
        CS->>DB: 本地关键词评分检索 KnowledgeChunk
    end
    CS->>CS: rerank + 多样性选择最多 5 个片段
    CS->>LLM: 构造 prompt 并调用模型
    alt 模型不可用
        CS->>CS: 本地摘要兜底
    end
    CS-->>FE: 答案 + 来源片段
```

## 7. 部署视图

```mermaid
flowchart TB
    subgraph Local["单机演示环境"]
        Browser["浏览器"]
        Vite["Vite Dev Server<br/>默认 5173"]
        Spring["Spring Boot<br/>8080"]
        H2["data/smart-exam.mv.db"]
        Uploads["uploads/"]
        Tesseract["tesseract 命令<br/>可选"]
        ES["Elasticsearch 9200<br/>可选"]
    end

    Browser --> Vite
    Vite --> Spring
    Spring --> H2
    Spring --> Uploads
    Spring --> Tesseract
    Spring --> ES
    Spring --> ModelAPI["外部 OpenAI 兼容 API<br/>可选"]
```

## 8. ER 图

```mermaid
erDiagram
    USERS ||--o{ STUDY_FOLDER : owns
    STUDY_FOLDER ||--o{ STUDY_FOLDER : parent
    STUDY_FOLDER ||--o{ STUDY_FILE : contains
    STUDY_FILE ||--o{ KNOWLEDGE_CHUNK : splits_to

    USERS ||--o| USER_AI_SETTINGS : configures
    USERS ||--o{ STUDY_PLAN_ITEM : owns

    USERS ||--o{ MISTAKE_STATUS : owns
    USERS ||--o{ MISTAKE_SUBJECT_TAG : owns
    USERS ||--o{ MISTAKE_QUESTION : owns
    MISTAKE_STATUS ||--o{ MISTAKE_QUESTION : classifies
    MISTAKE_QUESTION ||--o{ MISTAKE_ATTACHMENT : has
    MISTAKE_QUESTION }o--o{ MISTAKE_SUBJECT_TAG : tagged_by

    USERS {
        bigint id PK
        varchar username UK
        varchar password_hash
        varchar display_name
        timestamp created_at
    }

    STUDY_FOLDER {
        bigint id PK
        bigint owner_id FK
        bigint parent_id FK
        varchar name
        varchar description
        integer depth
        timestamp created_at
    }

    STUDY_FILE {
        bigint id PK
        bigint folder_id FK
        varchar tag
        varchar original_name
        varchar stored_path
        varchar content_type
        text extracted_text
        boolean knowledge_enabled
        timestamp uploaded_at
    }

    KNOWLEDGE_CHUNK {
        bigint id PK
        bigint file_id FK
        bigint folder_id FK
        integer chunk_index
        integer page_number
        text content
    }

    USER_AI_SETTINGS {
        bigint id PK
        bigint user_id FK
        varchar ai_role
        text system_prompt
        varchar chat_model
        varchar chat_endpoint
        varchar chat_api_key
        varchar embedding_model
        varchar embedding_endpoint
        varchar embedding_api_key
        integer embedding_dimensions
    }

    STUDY_PLAN_ITEM {
        bigint id PK
        bigint owner_id FK
        varchar title
        varchar subject
        varchar description
        varchar item_type
        date start_date
        time start_time
        time end_time
        varchar location
        varchar priority
        varchar status
        varchar source
        timestamp created_at
        timestamp updated_at
    }

    MISTAKE_QUESTION {
        bigint id PK
        bigint owner_id FK
        bigint status_id FK
        boolean mastered
        text question_text
        varchar question_original_name
        varchar question_stored_path
        varchar question_content_type
        text solution_text
        varchar solution_original_name
        varchar solution_stored_path
        varchar solution_content_type
        timestamp created_at
        timestamp updated_at
    }

    MISTAKE_ATTACHMENT {
        bigint id PK
        bigint mistake_id FK
        varchar type
        varchar original_name
        varchar display_name
        varchar stored_path
        varchar content_type
        timestamp created_at
    }

    MISTAKE_STATUS {
        bigint id PK
        bigint owner_id FK
        varchar name
        timestamp created_at
    }

    MISTAKE_SUBJECT_TAG {
        bigint id PK
        bigint owner_id FK
        varchar name
        timestamp created_at
    }
```

## 9. 主要接口分组

| 模块 | 接口前缀 | 说明 |
| --- | --- | --- |
| 认证 | `/api/auth` | 注册、登录 |
| 文件夹 | `/api/folders` | 文件夹增删改查 |
| 文件 | `/api/folders/{folderId}/files`、`/api/files` | 文件上传、查看、编辑、移动、删除、知识库状态 |
| 问答 | `/api/chat` | 普通问答、流式问答、会话生成笔记 |
| AI 设置 | `/api/ai-settings` | 读取和保存用户模型配置 |
| 学习计划 | `/api/study-plan` | 计划 CRUD、AI 规划讨论、生成、应用 |
| 错题 | `/api/mistakes` 等 | 错题、状态、标签、附件、练习 |

## 10. 架构特点与限制

架构特点：

- 前后端分离，接口边界清晰。
- 服务层统一进行用户资源归属校验，避免越权访问。
- 知识库问答采用“检索后生成”，支持来源引用。
- AI 与 Elasticsearch 均有降级路径，便于本地演示。
- 错题和学习计划模块与知识库模块相互独立，降低耦合。

当前限制：

- 默认 H2 数据库和本地上传目录不适合多人生产环境。
- API Key 以用户配置形式保存，生产环境应增加加密存储。
- 当前未实现管理员角色、班级/团队协作和多端同步。
- Elasticsearch 与 Tesseract 需要运行环境额外安装。
