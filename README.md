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
- 历史相册支持批量下载选中图片（优先 OSS，缺失时回退本地）

## 最近更新（2026-04）

- 密码登录现在默认保持 `7 天`；服务端会把会话状态写入 `data/sessions.json`，容器重启后仍可继续保持登录。
- `data/api-platforms.xml` 新增 `defaultModel` 属性，可为每个平台指定前台默认模型。
- 首页布局做了简洁化重构，字号和按钮密度更紧凑，交互区更聚焦高频操作。
- 首页“开始生成”按钮固定在左下角，不随滚动移动。
- 首页“当前结果”和“历史图片”不再直接展示完整提示词，改为通过按钮复制完整提示词，界面更干净。
- 历史相册卡片新增“复制提示词”按钮，可复制完整提示词内容。
- 历史相册“下载选中 ZIP”已改为“批量下载选中”，优先走 OSS 签名下载链接，未命中 OSS 时回退本地下载。
- 在以下图片上方新增悬浮快捷按钮，可一键发送到“基础结构图”或“风格参考图”：
  - 首页当前结果图
  - 首页历史缩略图
  - 历史相册页面图片（点击后会跳回首页并自动导入）
- 生成成功或失败时，浏览器标签页 `title` 会闪动提示；当页面重新被激活（可见且聚焦）后自动恢复默认标题。

## 本地启动

```bash
python3 server.py
```

启动后访问：

[http://127.0.0.1:8787](http://127.0.0.1:8787)

## 生成图平台配置

生成图相关的 `BANANA_PRO_API_URL`、`BANANA_PRO_IMAGE_MODEL` 和 `BANANA_PRO_API_KEY` 已独立到 [data/api-platforms.xml](./data/api-platforms.xml)。

一个平台对应一个 `url` 和一个 `key`，`models` 里可以用符号分隔多个模型；前台会在“图生图工作台”标题下自动显示“API 平台”和“生成模型”两个选择器。可选的 `defaultModel` 用来指定该平台在前台默认选中的模型。

默认示例：

```xml
<?xml version="1.0" encoding="utf-8"?>
<apiPlatforms version="1">
  <platform id="apiyi" name="APIYI" default="true" defaultModel="gemini-3-pro-image-preview">
    <url>https://api.apiyi.com/v1beta/models/{model}:generateContent</url>
    <key></key>
    <models separator="|">gemini-3-pro-image-preview|gemini-3.1-flash-image-preview-4k</models>
  </platform>
</apiPlatforms>
```

如果你有多个 API 平台，直接复制一个 `<platform>` 节点即可。
如果设置了 `defaultModel` 且它存在于 `models` 列表中，前台会优先选中它；未设置或不匹配时会回退到 `models` 的第一项。
如果仓库是公开的，建议提交前保持 `<key></key>` 为空，把真实 key 放在本地 `.env` / `.env.docker` 中。

## 环境变量

默认从项目根目录 `.env` 读取：

- `BANANA_PRO_UI_PASSWORD`
- `BANANA_PRO_LLM_API_KEY`
- `BANANA_PRO_LLM_API_URL`
- `BANANA_PRO_LLM_MODEL`
- `BANANA_PRO_HOST`
- `BANANA_PRO_PORT`
- `BANANA_PRO_OSS_ENABLED`
- `BANANA_PRO_OSS_ENDPOINT`
- `BANANA_PRO_OSS_BUCKET`
- `BANANA_PRO_OSS_ACCESS_KEY_ID`
- `BANANA_PRO_OSS_ACCESS_KEY_SECRET`
- `BANANA_PRO_OSS_PREFIX`
- `BANANA_PRO_OSS_PUBLIC_BASE_URL`

也可以直接通过环境变量传入，环境变量优先级高于 `.env`。

如果设置了 `BANANA_PRO_UI_PASSWORD`，页面会先要求输入访问密码；不设置则保持无密码访问。
登录成功后会默认保持 `7 天`；服务端会把会话状态保存到 `data/sessions.json`，该文件属于运行时数据，不建议提交到仓库。
如果设置了 `BANANA_PRO_LLM_API_KEY`，AI 翻译优化会优先使用它；未设置时会回退到默认图片平台的 key，兼容旧版时也会继续回退到 `BANANA_PRO_API_KEY`。
`BANANA_PRO_LLM_API_URL` 用于单独配置提示词优化所使用的 LLM 接口地址，默认值为 `https://api.apiyi.com/v1/chat/completions`。
`BANANA_PRO_LLM_MODEL` 用于单独配置提示词优化所使用的模型名，默认值为 `gpt-5.4`。
`BANANA_PRO_API_URL`、`BANANA_PRO_IMAGE_MODEL` 和 `BANANA_PRO_API_KEY` 仍保留为兼容旧配置和首次生成默认 XML 的回退项，但日常推荐直接编辑 `data/api-platforms.xml`。

推荐先复制示例文件：

```bash
cp .env.example .env
```

建议把真实 key 只保留在你本地 `.env` / `.env.docker` 中，仓库里的示例文件保持空值。

默认示例内容如下：

```env
# 生成图平台优先从 data/api-platforms.xml 读取。
# 以下 3 项仅在 XML 文件缺失或对应节点留空时作为回退。
BANANA_PRO_API_URL=https://api.apiyi.com/v1beta/models/{model}:generateContent
BANANA_PRO_IMAGE_MODEL=gemini-3-pro-image-preview
BANANA_PRO_API_KEY=
BANANA_PRO_UI_PASSWORD=
BANANA_PRO_LLM_API_KEY=
BANANA_PRO_LLM_API_URL=https://api.apiyi.com/v1/chat/completions
BANANA_PRO_LLM_MODEL=gpt-5.4
BANANA_PRO_HOST=127.0.0.1
BANANA_PRO_PORT=8787
BANANA_PRO_OSS_ENABLED=false
BANANA_PRO_OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
BANANA_PRO_OSS_BUCKET=
BANANA_PRO_OSS_ACCESS_KEY_ID=
BANANA_PRO_OSS_ACCESS_KEY_SECRET=
BANANA_PRO_OSS_PREFIX=banana-pro
BANANA_PRO_OSS_PUBLIC_BASE_URL=
```

### 阿里云 OSS（可选）

开启后，服务端会在每次生成成功后，把“原图 + 缩略图”上传到 OSS，并在历史记录里额外写入：

- `ossImageUrl`
- `ossThumbUrl`
- `ossImageKey`
- `ossThumbKey`
- `ossMetadataXmlUrl`
- `ossMetadataXmlKey`

默认仍保留本地文件和本地 `imageUrl` / `thumbUrl`，不会影响现有页面与历史管理逻辑。
OSS 对象会按 `YYMM` 文件夹归类：主图在 `YYMM/...`，参数 XML 在 `YYMM/XML/...`（缩略图在 `YYMM/thumbs/...`）；删除历史记录时仅删除本地文件，不删除 OSS 对象。
当 OSS 上传失败时，生成流程仍返回成功，错误信息会记录在 `ossUploadError` 字段中。

关键配置说明：

- `BANANA_PRO_OSS_ENABLED=true`：启用 OSS 上传。
- `BANANA_PRO_OSS_ENDPOINT`：例如 `oss-cn-hangzhou.aliyuncs.com`。
- `BANANA_PRO_OSS_BUCKET`：Bucket 名称。
- `BANANA_PRO_OSS_ACCESS_KEY_ID` / `BANANA_PRO_OSS_ACCESS_KEY_SECRET`：访问凭证。
- `BANANA_PRO_OSS_PREFIX`：对象前缀目录，例如 `banana-pro`。
- `BANANA_PRO_OSS_PUBLIC_BASE_URL`：可选；若你绑定了 CDN / 自定义域名，可在这里填公开访问前缀。

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

先编辑 `data/api-platforms.xml`，把图片平台的 `url`、`key` 和 `models` 配好。然后再准备其余环境变量：

```bash
export BANANA_PRO_LLM_API_KEY="你的_llm_api_key"
export BANANA_PRO_LLM_API_URL="https://api.apiyi.com/v1/chat/completions"
export BANANA_PRO_LLM_MODEL="gpt-5.4"
export BANANA_PRO_UI_PASSWORD=""
export BANANA_PRO_HOST="0.0.0.0"
export BANANA_PRO_PORT="8787"
export BANANA_PRO_OSS_ENABLED="false"
export BANANA_PRO_OSS_ENDPOINT="oss-cn-hangzhou.aliyuncs.com"
export BANANA_PRO_OSS_BUCKET="your_bucket_name"
export BANANA_PRO_OSS_ACCESS_KEY_ID="your_access_key_id"
export BANANA_PRO_OSS_ACCESS_KEY_SECRET="your_access_key_secret"
export BANANA_PRO_OSS_PREFIX="banana-pro"
export BANANA_PRO_OSS_PUBLIC_BASE_URL=""
docker compose up -d --build
```

也可以直接复制一份 Docker 专用环境文件：

```bash
cp .env.docker.example .env.docker
```

然后把 `.env.docker` 里的 `BANANA_PRO_LLM_API_KEY`、`BANANA_PRO_LLM_API_URL` 和 `BANANA_PRO_LLM_MODEL` 按需改成你自己的；如果不需要访问密码，可以保持 `BANANA_PRO_UI_PASSWORD=` 为空。图片生成平台仍然直接改 `data/api-platforms.xml`。`.env.docker` 建议只作为本地私有文件使用，不要提交到仓库。再启动：

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
  -e BANANA_PRO_LLM_API_KEY="你的_llm_api_key" \
  -e BANANA_PRO_LLM_API_URL="https://api.apiyi.com/v1/chat/completions" \
  -e BANANA_PRO_LLM_MODEL="gpt-5.4" \
  -e BANANA_PRO_UI_PASSWORD="" \
  -e BANANA_PRO_HOST="0.0.0.0" \
  -e BANANA_PRO_PORT="8787" \
  -v "$(pwd)/data:/app/data" \
  banana-pro-ui
```

## GitHub 自动推送到 Docker Hub

项目已包含 GitHub Actions 工作流文件：[.github/workflows/docker.yml](./.github/workflows/docker.yml)。

当代码推送到 `main` 分支时，GitHub 会自动构建镜像并推送到 Docker Hub 仓库 `mr926/banana-pro-webui-apiyi`。

## iOS 原生客户端

项目里已经生成了一个新的 iOS 原生客户端工程，位置在：

- [`ios/BananaLab/BananaLab.xcodeproj`](./ios/BananaLab/BananaLab.xcodeproj)

说明：

- 使用 `SwiftUI + MVVM`
- 目标平台是最新 iOS 版本，并且支持 iPad
- 默认服务器地址和会话恢复都已接好
- 图片选择走相册，历史图支持 OSS 优先加载与下载
- 生成成功/失败会触发系统通知

打包测试时，用 Xcode 打开这个工程，选择对应的 `iPhone` 或 `iPad` 设备/模拟器后直接运行即可。如果要在真机上安装测试包，再去 Xcode 里补签名团队和证书。

## Android 原生版

原生 Android 工程已经开始放在：

- `android/BananaLab`

它会继续沿用当前后端接口，目标是做成 `BananaLab` 原生 App。

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
