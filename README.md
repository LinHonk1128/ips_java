# 智能考研系统

基于 Spring Boot + Vue 的个人考研知识库系统，支持用户登录、科目文件夹、资料上传、OCR/文本抽取、知识库构建，以及基于用户 API Key 的智能问答。

## 功能

- 用户注册与登录，后端使用 JWT 鉴权。
- 个人空间内创建科目文件夹。
- 文件上传到指定文件夹，并标记为教材、资料、笔记、习题或其他。
- 支持 PDF、Word、图片、文本文件上传。
- 上传后自动抽取文本；图片 OCR 默认调用本机 `tesseract` 命令。
- 抽取文本可在前端即时编辑，保存后切分为知识片段。
- 答疑模式：基于当前文件夹知识库回答，并返回来源片段。
- 教师模式：AI 模拟学生，基于知识点向用户提问。
- 支持 OpenAI 兼容接口，可填写模型名、Endpoint 和自己的 API Key。

## 项目结构

```text
backend/   Spring Boot 后端
frontend/  Vue + Vite 前端
uploads/   运行后自动生成的本地上传目录
data/      运行后自动生成的 H2 数据库目录
```

## 启动后端

```powershell
cd backend
mvn spring-boot:run
```

默认地址：

- API: `http://localhost:8080/api`
- H2 Console: `http://localhost:8080/h2-console`

H2 连接信息见 `backend/src/main/resources/application.yml`。

## 启动前端

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

如果后端地址不是 `http://localhost:8080/api`，可设置：

```powershell
$env:VITE_API_BASE="http://localhost:8080/api"
npm.cmd run dev
```

## OCR 配置

图片 OCR 通过本机 Tesseract 调用：

```yaml
app:
  ocr:
    tesseract-command: "tesseract"
```

需要安装 Tesseract 与中文语言包 `chi_sim`。如果未安装，系统仍可上传图片，并在编辑器中手动补全文本后保存为知识库。

## 大模型接口

前端可填写：

- 模型：如 `gpt-4o-mini`、`qwen-plus`、`deepseek-chat`
- Endpoint：OpenAI 兼容接口地址，例如 `/v1/chat/completions`
- API Key：用户自己的 Key

未填写 API Key 时，后端会使用本地检索结果生成简易回答，便于离线演示。
