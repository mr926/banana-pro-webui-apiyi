# Banana Pro 图生图 Web UI

基于 GRSAI 图片生成 API 的本地 Web UI，支持 Nano Banana、GPT Image、多参考图和 OSS 存储。

## 功能

- 基础图必传，最多 6 张参考图
- 基础图超过 `4MB` 自动压缩；参考图超过 `2MB` 自动压缩
- 生成尺寸支持 `1K / 2K / 4K`，比例自动继承基础图或手动选择
- 多图显式编号 `##BASE## / ##REF1##...`，可在提示词中按编号引用
- 提示词库下拉选择 + 弹窗管理（`data/prompt-library.md`）
- 历史相册，支持批量下载（优先 OSS，缺失时回退本地）
- 支持 PWA 安装到桌面 / 主屏幕
- 上传 / 下载 / 通知根据桌面 / iPhone / Android 自动适配

## Docker 部署

直接使用 Docker Hub 镜像，无需本地构建。把下面的 `docker-compose.yml` 保存后按注释填写，运行 `docker compose up -d` 即可。

```yaml
services:
  banana-pro-ui:
    image: mr926/banana-pro-webui-apiyi:latest
    container_name: banana-pro-ui
    ports:
      - "8787:8787"          # 左边可改为你想要的宿主机端口
    environment:

      # ── 访问控制 ─────────────────────────────────────────────────────────
      # 页面访问密码，留空则无密码保护；登录后保持 7 天
      BANANA_PRO_UI_PASSWORD: "your_password"

      # ── 服务监听 ─────────────────────────────────────────────────────────
      # 容器内必须是 0.0.0.0，否则宿主机无法访问
      BANANA_PRO_HOST: "0.0.0.0"
      # 需与上方 ports 右侧端口一致
      BANANA_PRO_PORT: "8787"

      # ── 图片生成平台（回退配置）─────────────────────────────────────────
      # 优先读取 data/api-platforms.xml；以下仅在 XML 缺失或 key 为空时生效
      BANANA_PRO_API_KEY: ""
      BANANA_PRO_API_URL: "https://api.apiyi.com/v1beta/models/{model}:generateContent"
      BANANA_PRO_IMAGE_MODEL: "gemini-3-pro-image-preview"

      # ── 阿里云 OSS（可选）───────────────────────────────────────────────
      # 改为 true 开启；生成成功后自动上传原图 + 缩略图到 OSS
      BANANA_PRO_OSS_ENABLED: "false"
      # OSS 地域 Endpoint（不含 bucket 前缀）
      BANANA_PRO_OSS_ENDPOINT: "oss-cn-hangzhou.aliyuncs.com"
      # Bucket 名称
      BANANA_PRO_OSS_BUCKET: "my-bucket"
      # 阿里云 AccessKey ID
      BANANA_PRO_OSS_ACCESS_KEY_ID: "LTAI5tXxxxxxxxxxxxxxxxxx"
      # 阿里云 AccessKey Secret
      BANANA_PRO_OSS_ACCESS_KEY_SECRET: "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
      # 对象存储前缀目录，图片按 YYMM 子目录归档
      BANANA_PRO_OSS_PREFIX: "banana-pro"
      # 可选：绑定了 CDN / 自定义域名时填写，否则留空
      BANANA_PRO_OSS_PUBLIC_BASE_URL: ""

    volumes:
      # 持久化历史记录、生成图片、平台配置和技能文件
      - ./data:/app/data
    restart: unless-stopped
```

```bash
docker compose up -d
```

访问：[http://127.0.0.1:8787](http://127.0.0.1:8787)

## 生成图平台配置

图片平台的 `url`、`key`、`models` 推荐在 `data/api-platforms.xml` 中配置，支持多平台切换：

```xml
<?xml version="1.0" encoding="utf-8"?>
<apiPlatforms version="1">
  <platform id="Grsai" name="Grsai" default="true" defaultModel="nano-banana-2">
    <url>https://grsai.dakka.com.cn/v1/api/generate</url>
    <key></key>
    <models separator="|" protocol="grsai-generate">nano-banana-2</models>
  </platform>
</apiPlatforms>
```

Grsai 使用 `/v1/api/generate` 时，请求体会按 Grsai 文档发送 `model / prompt / images / aspectRatio / imageSize / replyType`；若接口返回异步 `id`，服务端会继续查询 `/v1/api/result` 并下载 `results[].url` 到本地历史。

多个平台直接复制 `<platform>` 节点。`key` 建议在本地填写，不要提交到仓库。

`models` 的 `protocol` 属性是可选的，旧配置不填写时仍会根据 URL 和模型名自动识别。当前支持：

- `nanobananapro`（也可写 `gemini-generate-content`）：Gemini `generateContent` JSON 协议
- `grsai-generate`：Grsai `/v1/api/generate` 协议
- `openai-images`：OpenAI Images API 兼容协议

同一个平台可以配置多个 `<models>` 节点。页面会合并展示其中的模型，服务端会按当前所选模型使用对应协议：

```xml
<platform id="mixed-provider" name="Mixed Provider">
  <url>https://example.com/v1</url>
  <key>your-api-key</key>
  <models separator="|" protocol="gemini-generate-content">nano-banana-pro|nano-banana-2</models>
  <models separator="|" protocol="openai-images">gpt-image-2-vip</models>
</platform>
```

这种写法要求平台的 `url` 是两种协议共用的基础地址；如果两类模型需要不同 URL，请拆成两个 `<platform>`。

GPT Image 2 平台示例：

```xml
<platform id="openai-compatible" name="GPT Image 2" defaultModel="gpt-image-2">
  <url>https://api.openai.com/v1</url>
  <key>your-api-key</key>
  <models separator="|" protocol="openai-images">gpt-image-2</models>
</platform>
```

`openai-images` 在纯文生图时调用 `/images/generations`；提供基础图或参考图时调用 `/images/edits`，并按基础图、参考图上传顺序发送多个 `image[]` 文件。现有的比例和 `1K / 2K / 4K` 选项会自动转换为 GPT Image 2 支持的具体分辨率。

## 提示词

- **提示词库**：`data/prompt-library.md`，一行一条，在页面"管理提示词"弹窗中编辑

## 本地启动

```bash
cp .env.example .env   # 按需填写 .env
python3 server.py
```

访问：[http://127.0.0.1:8787](http://127.0.0.1:8787)
