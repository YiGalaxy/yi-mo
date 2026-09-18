<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { pingApi, type PingResult } from '@/api/ping'
import type { ApiError } from '@/api/http'

const ping = ref<PingResult | null>(null)
const error = ref<ApiError | null>(null)
const loading = ref(true)

async function check() {
  loading.value = true
  error.value = null
  try {
    ping.value = await pingApi.ping()
  } catch (e) {
    error.value = e as ApiError
  } finally {
    loading.value = false
  }
}

onMounted(check)
</script>

<template>
  <div class="min-h-screen bg-neutral-50 flex items-center justify-center p-8">
    <div class="w-full max-w-lg">
      <h1 class="text-2xl font-semibold text-neutral-800">亿墨 YI-MO</h1>
      <p class="text-sm text-neutral-500 mt-1 mb-6">本地优先、零注册、Agent 增强的小说创作工作台</p>

      <!-- 加载中 -->
      <div v-if="loading" class="text-sm text-neutral-500">正在连接后端…</div>

      <!-- 连接失败 -->
      <div v-else-if="error" class="rounded-lg border border-red-200 bg-red-50 p-4">
        <p class="text-sm font-medium text-red-800">后端连接失败</p>
        <p class="text-xs text-red-600 font-mono mt-1">{{ error.error }}</p>
        <p class="text-xs text-red-600 mt-1">{{ error.message }}</p>
        <p class="text-xs text-red-500 mt-3 leading-relaxed">
          确认后端已启动：<br />
          <code class="bg-red-100 px-1 rounded">cd backend &amp;&amp; mvn spring-boot:run</code>
        </p>
        <button
          class="mt-3 text-xs px-3 py-1.5 rounded border border-red-300 text-red-700 hover:bg-red-100"
          @click="check"
        >
          重试
        </button>
      </div>

      <!-- 连接成功 -->
      <div v-else-if="ping" class="rounded-lg border border-green-200 bg-green-50 p-4">
        <p class="text-sm font-medium text-green-800 mb-3">前后端已连通</p>
        <dl class="text-xs text-green-700 font-mono space-y-1.5">
          <div class="flex gap-3">
            <dt class="w-20 text-green-600 shrink-0">service</dt>
            <dd>{{ ping.service }}</dd>
          </div>
          <div class="flex gap-3">
            <dt class="w-20 text-green-600 shrink-0">server time</dt>
            <dd>{{ ping.time }}</dd>
          </div>
          <div class="flex gap-3">
            <dt class="w-20 text-green-600 shrink-0">sample id</dt>
            <dd>{{ ping.sampleId }}</dd>
          </div>
        </dl>
        <p class="text-xs text-green-600 mt-3 leading-relaxed">
          sample id 是后端生成的 ULID（26 字符 + 类型前缀）。<br />
          如果它看起来正常，说明 ID 生成、JSON 序列化、跨域代理都通了。
        </p>
      </div>

      <p class="text-xs text-neutral-400 mt-8">
        下一步：第 1 章书库管理 —— 添加一个文件夹，让它出现在列表里。
      </p>
    </div>
  </div>
</template>
