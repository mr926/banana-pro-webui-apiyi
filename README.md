# Banana Pro 图生图 Web UI

一个无需额外依赖的本地 Web UI，支持：

- 基础图必传
- 最多 6 张参考图
- 基础结构图超过 `4MB` 时自动压缩为 `JPG`，长边缩到 `4000px`，压缩率 `85%`
- 风格参考图超过 `2MB` 时自动压缩到 `2MB` 以内
- 图片比例可选，默认自动继承基础图比例
- 生成大小支持 `1K / 2K / 4K`
- 可选开启网络搜索增强
- 提示词列表下拉选择，并支持弹窗管理
- AI 翻译优化模式，支持选择和管理提示词优化人格
- 结果图直接下载
- 本地保留历史图片和历史记录

## 本地启动

```bash
python3 server.py
```

启动后访问：

[http://127.0.0.1:8787](http://127.0.0.1:8787)

## 环境变量

默认从项目根目录 `.env` 读取：

- `BANANA_PRO_API_URL`
- `BANANA_PRO_UI_PASSWORD`
- `BANANA_PRO_API_KEY`
- `BANANA_PRO_LLM_API_KEY`
- `BANANA_PRO_HOST`
- `BANANA_PRO_PORT`

也可以直接通过环境变量传入，环境变量优先级高于 `.env`。

如果设置了 `BANANA_PRO_UI_PASSWORD`，页面会先要求输入访问密码；不设置则保持无密码访问。
如果设置了 `BANANA_PRO_LLM_API_KEY`，AI 翻译优化会优先使用它；未设置时会回退到 `BANANA_PRO_API_KEY`。

推荐先复制示例文件：

```bash
cp .env.example .env
```

默认示例内容如下：

```env
BANANA_PRO_API_URL=https://api.apiyi.com/v1beta/models/gemini-3-pro-image-preview:generateContent
BANANA_PRO_UI_PASSWORD=
BANANA_PRO_API_KEY=
BANANA_PRO_LLM_API_KEY=
BANANA_PRO_HOST=127.0.0.1
BANANA_PRO_PORT=8787
```

## 提示词与人格管理

### 提示词列表

页面中的“管理提示词”按钮会打开弹窗，内容保存到：

- `data/prompt-library.md`

文件规则：

- 一行对应一个下拉列表选项
- 空行会被忽略

### 提示词优化人格

“AI 翻译优化”区域中的“管理”按钮会打开人格管理弹窗，支持：

- 上传新的 `.md` 人格文件
- 编辑已有 `.md` 人格文件
- 删除已有 `.md` 人格文件

人格文件保存在：

- `data/personas/*.md`

每个人格文件格式为：

```md
第一行：人格名称
第二行：一句简介
第三行开始：人格正文 / system prompt
```

## Docker 启动

先准备环境变量，推荐直接在命令行传入，避免把 key 写进镜像里：

```bash
export BANANA_PRO_LLM_API_KEY="你的_llm_api_key"
export BANANA_PRO_API_KEY="你的_api_key"
export BANANA_PRO_API_URL="https://api.apiyi.com/v1beta/models/gemini-3-pro-image-preview:generateContent"
export BANANA_PRO_UI_PASSWORD=""
export BANANA_PRO_HOST="0.0.0.0"
export BANANA_PRO_PORT="8787"
docker compose up -d --build
```

也可以直接复制一份 Docker 专用环境文件：

```bash
cp .env.docker.example .env.docker
```

然后把 `.env.docker` 里的 `BANANA_PRO_API_KEY` 和 `BANANA_PRO_LLM_API_KEY` 改成你自己的；如果不需要访问密码，可以保持 `BANANA_PRO_UI_PASSWORD=` 为空。再启动：

```bash
docker compose --env-file .env.docker up -d --build
```

启动后访问：

[http://127.0.0.1:8787](http://127.0.0.1:8787)

停止：

```bash
docker compose down
```

如果你更喜欢单独 `docker build` / `docker run`，也可以：

```bash
docker build -t banana-pro-ui .
docker run -d \
  --name banana-pro-ui \
  -p 8787:8787 \
  -e BANANA_PRO_API_KEY="你的_api_key" \
  -e BANANA_PRO_LLM_API_KEY="你的_llm_api_key" \
  -e BANANA_PRO_API_URL="https://api.apiyi.com/v1beta/models/gemini-3-pro-image-preview:generateContent" \
  -e BANANA_PRO_UI_PASSWORD="" \
  -e BANANA_PRO_HOST="0.0.0.0" \
  -e BANANA_PRO_PORT="8787" \
  -v "$(pwd)/data:/app/data" \
  banana-pro-ui
```

## GitHub 自动推送到 Docker Hub

项目已包含 GitHub Actions 工作流文件：[.github/workflows/docker.yml](/Users/chao/Desktop/banana/.github/workflows/docker.yml)。

当代码推送到 `main` 分支时，GitHub 会自动构建镜像并推送到 Docker Hub 仓库 `mr926/banana-pro-webui-apiyi`。

使用前需要先在 GitHub 仓库的 `Settings -> Secrets and variables -> Actions` 中添加：

- `DOCKERHUB_USERNAME`: 你的 Docker Hub 用户名
- `DOCKERHUB_TOKEN`: 你的 Docker Hub Access Token

添加完成后，只要向 `main` 推送代码，或在 GitHub Actions 页面手动触发该工作流，就会自动发布镜像。

## 目录说明

- `server.py`: 本地服务和 API 代理
- `public/`: 前端页面
- `data/generated/`: 生成后的图片文件
- `data/history.json`: 历史记录
- `data/prompt-library.md`: 提示词列表，一行一条
- `data/personas/`: 提示词优化人格 `.md` 文件目录
- `Dockerfile`: Docker 镜像构建文件
- `docker-compose.yml`: Docker Compose 启动配置
- `.env.example`: 本地环境变量示例
- `.env.docker.example`: Docker 环境变量示例
- `.github/workflows/docker.yml`: 自动构建并推送 Docker Hub 的 GitHub Actions 工作流
