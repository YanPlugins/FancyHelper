#!/bin/bash
set -e

# 输入参数
VERSION="${1}"
REPO="${2}"
GITHUB_SHA="${3}"
LAST_TAG="${4}"

echo "开始生成发行注记..."
echo "版本号: $VERSION"
echo "仓库: $REPO"
echo "当前SHA: $GITHUB_SHA"
echo "上次Tag: $LAST_TAG"

# 创建临时目录
mkdir -p /tmp/release-notes

# 获取 PR 变化
echo "正在获取 PR 变化..."
if [[ -n "$LAST_TAG" ]]; then
  PRS=$(gh api \
    "repos/${REPO}/pulls" \
    --jq '.[] | select(.merged_at != null and .merged_at > "'$(git log -1 --format=%cI "$LAST_TAG")'") | "- \(.title)"' 2>/dev/null || echo "")
else
  PRS=$(gh api \
    "repos/${REPO}/pulls?state=closed&sort=updated&direction=desc" \
    --jq '.[] | select(.merged == true) | "- \(.title)"' -10 2>/dev/null || echo "")
fi
echo "PRS: $PRS"

# 获取 Commit 变化
echo "正在获取 Commit 变化..."
if [[ -n "$LAST_TAG" ]]; then
  COMMITS=$(git log "$LAST_TAG"..HEAD --pretty=format:"- %s" --no-merges 2>/dev/null || echo "")
else
  COMMITS=$(git log -20 --pretty=format:"- %s" --no-merges 2>/dev/null || echo "")
fi
echo "Commits: $COMMITS"

# 调用 CloudFlare AI 生成 Overview
echo "正在调用 AI 生成 Overview..."
ACCOUNT_ID=$(curl -s -H "Authorization: Bearer ${CLOUDFLARE_API_KEY}" \
  "https://api.cloudflare.com/client/v4/accounts" | jq -r '.result[0].id')

if [[ -z "$ACCOUNT_ID" || "$ACCOUNT_ID" == "null" ]]; then
  echo "无法获取 CloudFlare Account ID，使用默认Overview"
  cat > /tmp/release-notes/overview.md << 'EOF'
## Overview
* 本次更新包含多项功能改进和问题修复
EOF
else
  echo "Account ID: $ACCOUNT_ID"

  # 构建 prompt 并写入文件
  cat > /tmp/release-notes/prompt.txt << EOF
你是一个专业的版本发布助手。请根据以下 Pull Request 和 Commit 信息，为 FancyHelper Minecraft 插件生成中文的发行注记 Overview。

版本号: v$VERSION

PR 列表:
$PRS

Commit 列表:
$COMMITS

要求：
1. 用中文撰写
2. 必须使用以下格式（严格遵守）：
   ## Overview
   * 第一条变更说明
   * 第二条变更说明
   * 第三条变更说明
3. 每条变更必须以 "* " 开头（星号+空格），不要使用其他符号
4. 简洁明了，每条控制在 10-30 字
5. 突出主要功能更新、bug修复和重要改动
6. 不要添加 emoji
7. 直接输出内容，不要有开场白
8. 总条目控制在 3-6 条

请生成发行注记 Overview：
EOF

  echo "Prompt 内容:"
  cat /tmp/release-notes/prompt.txt
  echo "---"

  # 构建 input 数组
  SYSTEM_MSG="你是一个专业的版本发布助手，擅长撰写简洁明了的中文发行注记。输出格式必须严格遵守用户要求。"
  USER_MSG=$(cat /tmp/release-notes/prompt.txt)

  # 使用 jq 构建 JSON
  REQUEST_BODY=$(jq -n \
    --arg model "@cf/openai/gpt-oss-120b" \
    --arg system "$SYSTEM_MSG" \
    --arg user "$USER_MSG" \
    '{
      "model": $model,
      "input": [
        {"role": "system", "content": $system},
        {"role": "user", "content": $user}
      ],
      "max_tokens": 500,
      "reasoning": {"effort": "medium", "summary": "detailed"}
    }')

  echo "Request Body 长度: ${#REQUEST_BODY}"

  # 调用 CloudFlare AI Responses API
  echo "正在调用 CloudFlare AI Responses API..."
  RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST \
    "https://api.cloudflare.com/client/v4/accounts/$ACCOUNT_ID/ai/v1/responses" \
    -H "Authorization: Bearer ${CLOUDFLARE_API_KEY}" \
    -H "Content-Type: application/json" \
    -d "$REQUEST_BODY")

  # 分离 HTTP 状态码和响应体
  HTTP_CODE=$(echo "$RESPONSE" | grep "HTTP_CODE:" | cut -d':' -f2)
  RESPONSE_BODY=$(echo "$RESPONSE" | sed '/HTTP_CODE:/d')

  echo "HTTP 状态码: $HTTP_CODE"
  echo "API 响应:"
  echo "$RESPONSE_BODY" | head -c 2000
  echo ""

  # 将响应保存到文件
  echo "$RESPONSE_BODY" > /tmp/release-notes/api_response.json
  echo "完整响应已保存到 /tmp/release-notes/api_response.json"

  # 解析响应并提取文本
  echo "$RESPONSE_BODY" | jq -r '.output[] | select(.type == "message") | .content[] | select(.type == "output_text") | .text // empty' > /tmp/release-notes/overview.md 2>/dev/null

  # 检查文件是否为空
  if [[ ! -s /tmp/release-notes/overview.md ]]; then
    echo "尝试 result.response 格式..."
    echo "$RESPONSE_BODY" | jq -r '.result.response // empty' > /tmp/release-notes/overview.md 2>/dev/null
  fi

  # 检查文件是否为空
  if [[ ! -s /tmp/release-notes/overview.md ]]; then
    echo "尝试 OpenAI 兼容格式..."
    echo "$RESPONSE_BODY" | jq -r '.choices[0].message.content // empty' > /tmp/release-notes/overview.md 2>/dev/null
  fi

  # 检查文件是否为空或只有空白
  OVERVIEW_CONTENT=$(cat /tmp/release-notes/overview.md | tr -d '[:space:]')
  if [[ -z "$OVERVIEW_CONTENT" ]]; then
    ERROR_MSG=$(echo "$RESPONSE_BODY" | jq -r '.errors[0].message // .error // empty' 2>/dev/null)
    if [[ -n "$ERROR_MSG" && "$ERROR_MSG" != "null" ]]; then
      echo "API 错误: $ERROR_MSG"
    fi
    echo "AI 响应为空，使用默认Overview"
    cat > /tmp/release-notes/overview.md << 'EOF'
## Overview
* 本次更新包含多项功能改进和问题修复
EOF
  else
    echo "AI Overview 生成成功，内容如下:"
    echo "=== Overview 开始 ==="
    cat /tmp/release-notes/overview.md
    echo "=== Overview 结束 ==="
  fi
fi

# 获取 GitHub 自动生成的 Release Notes
echo "正在获取 GitHub Release Notes..."
echo "Tag: v${VERSION}"
echo "Target: ${GITHUB_SHA}"
echo "Previous Tag: ${LAST_TAG}"

# 构建参数
API_ARGS=("repos/${REPO}/releases/generate-notes")
API_ARGS+=("-f" "tag_name=v${VERSION}")
API_ARGS+=("-f" "target_commitish=${GITHUB_SHA}")

if [[ -n "$LAST_TAG" ]]; then
  API_ARGS+=("-f" "previous_tag_name=${LAST_TAG}")
fi

# 调用 API
NOTES=$(gh api "${API_ARGS[@]}" --jq '.body' || echo "")

if [[ -z "$NOTES" ]]; then
  echo "GitHub Release Notes 获取为空或失败"
  echo "" > /tmp/release-notes/github_notes.md
else
  echo "GitHub Release Notes 获取成功"
  echo "$NOTES" > /tmp/release-notes/github_notes.md
  echo "内容预览:"
  head -n 10 /tmp/release-notes/github_notes.md
fi

# 组合最终的 Release Body
echo "正在组合 Release Body..."
BODY_FILE="/tmp/release_notes/release_body.md"

# 清空文件
> "$BODY_FILE"

# 添加 Overview
if [[ -s /tmp/release-notes/overview.md ]]; then
  cat /tmp/release-notes/overview.md >> "$BODY_FILE"
  echo "" >> "$BODY_FILE"
  echo "" >> "$BODY_FILE"
fi

# 添加 GitHub Notes
if [[ -s /tmp/release-notes/github_notes.md ]]; then
  cat /tmp/release-notes/github_notes.md >> "$BODY_FILE"
fi

# 如果都为空，使用默认内容
if [[ ! -s "$BODY_FILE" ]]; then
  echo "本次更新包含多项功能改进和问题修复。" >> "$BODY_FILE"
fi

echo "Release Body 已生成:"
cat "$BODY_FILE"

# 输出文件路径以便后续步骤使用
echo "/tmp/release_notes/release_body.md"