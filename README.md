# Banana Pro 图生图 Web UI

基于 Gemini 图生图 API 的本地 Web UI，支持多平台、多参考图、AI 提示词优化和 OSS 存储。

## 功能

- 基础图必传，最多 6 张参考图
- 基础图超过 `4MB` 自动压缩；参考图超过 `2MB` 自动压缩
- 生成尺寸支持 `1K / 2K / 4K`，比例自动继承基础图或手动选择
- 多图显式编号 `##BASE## / ##REF1##...`，可在提示词中按编号引用
- AI 翻译优化：把基础图和提示词一起发给 LLM 优化后再生图
- 提示词库下拉选择 + 弹窗管理（`data/prompt-library.md`）
- 提示词优化技能管理（`data/skills/*.md`）
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

      # ── AI 提示词优化（LLM）─────────────────────────────────────────────
      # 用于"AI 翻译优化"功能的 API Key（优先级高于图片平台 key）
      BANANA_PRO_LLM_API_KEY: "sk-xxxxxxxxxxxxxxxxxxxxxxxx"
      # 兼容 OpenAI 格式的 LLM 接口地址
      BANANA_PRO_LLM_API_URL: "https://api.apiyi.com/v1/chat/completions"
      # LLM 模型名
      BANANA_PRO_LLM_MODEL: "gpt-5.4"

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
  <platform id="apiyi" name="APIYI" default="true" defaultModel="gemini-3-pro-image-preview">
    <url>https://api.apiyi.com/v1beta/models/{model}:generateContent</url>
    <key></key>
    <models separator="|">gemini-3-pro-image-preview|gemini-3.1-flash-image-preview-4k</models>
  </platform>
</apiPlatforms>
```

多个平台直接复制 `<platform>` 节点。`key` 建议在本地填写，不要提交到仓库。

## 提示词与技能

- **提示词库**：`data/prompt-library.md`，一行一条，在页面"管理提示词"弹窗中编辑
- **优化技能**：`data/skills/*.md`，在"AI 翻译优化"→"管理"弹窗中上传 / 编辑 / 删除

技能文件格式：

```
第一行：技能名称
第二行：一句简介
第三行起：技能正文 / system prompt
```

## 本地启动

```bash
cp .env.example .env   # 按需填写 .env
python3 server.py
```

访问：[http://127.0.0.1:8787](http://127.0.0.1:8787)
