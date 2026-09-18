<script setup lang="ts">
import { ref, onMounted, computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NTree, NTag, NButton, useMessage, type TreeOption } from 'naive-ui'
import { libraryApi, type BookNode } from '@/api/library'
import { toApiError } from '@/api/http'

const route = useRoute()
const router = useRouter()
const message = useMessage()

// 从地址栏拿书库 id。路由是 /libraries/:libraryId/tree
const libraryId = route.params.libraryId as string

const books = ref<BookNode[]>([])
const loading = ref(true)
const scanning = ref(false)
const error = ref('')

/** 拉取卷章树 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await libraryApi.tree(libraryId)
    books.value = res.books
  } catch (e) {
    error.value = toApiError(e).message
  } finally {
    loading.value = false
  }
}

/**
 * 重新扫描。
 *
 * 这一步会读遍书库里的所有 .md 文件，大书库可能要几十秒。
 * 所以按钮上有转圈，期间不要重复点。
 */
async function rescan() {
  scanning.value = true
  try {
    const res = await libraryApi.rescan(libraryId)
    message.success(`扫描完成：${res.total} 个文件，索引 ${res.indexed} 个章节`)

    // 有解析失败的文件时单独提示——不能假装一切正常
    if (res.errors.length > 0) {
      message.warning(`${res.errors.length} 个文件解析失败，详见后端日志`)
    }

    await load()
  } catch (e) {
    message.error(toApiError(e).message)
  } finally {
    scanning.value = false
  }
}

/**
 * 点击树节点。
 *
 * 节点 key 带了类型前缀（book: / volume: / chapter:），
 * 因为 NTree 只会告诉你「哪个 key 被选中了」，不会告诉你它是哪一层。
 * 加前缀就能区分：只有 chapter: 开头的才跳转。
 */
function handleSelect(keys: Array<string | number>) {
  const key = String(keys[0] ?? '')
  if (key.startsWith('chapter:')) {
    router.push(`/chapters/${key.slice('chapter:'.length)}`)
  }
}

/**
 * 把后端的三层结构转成 NTree 要的格式。
 *
 * NTree 的每个节点是 { key, label, children }。
 * label 可以是字符串，也可以是一个渲染函数——这里用它来
 * 在章节名后面挂批注角标。
 */
const treeData = computed<TreeOption[]>(() =>
  books.value.map((book): TreeOption => ({
    key: `book:${book.name}`,
    label: `${book.name}（${book.totalWords} 字，${book.chapterCount} 章）`,
    children: book.volumes.map((volume): TreeOption => ({
      key: `volume:${book.name}:${volume.name}`,
      // 后端用空串表示「未分卷」，这里换成好读的文字
      label: volume.name || '未分卷',
      children: volume.chapters.map((ch): TreeOption => ({
        key: `chapter:${ch.id}`,
        label: ch.title,
        // 角标挂在 suffix 上，不用拼进 label。
        //
        // 注意 TreeOption.label 的类型就是 string（见 Naive UI 的
        // interface.d.ts），不是 string | (() => VNodeChild)——传函数会报类型错。
        // 要渲染 VNode，只能用 prefix / suffix 这两个字段
        suffix: () =>
          ch.pendingReviewCount > 0
            ? h(NTag, { size: 'tiny', type: 'error', round: true },
                { default: () => ch.pendingReviewCount })
            : null,
      })),
    })),
  })),
)

onMounted(load)
</script>

<template>
  <div class="p-6 max-w-3xl mx-auto">
    <div class="flex items-center justify-between mb-5">
      <div>
        <h2 class="text-lg font-medium text-neutral-800">章节</h2>
        <p class="text-xs text-neutral-400 mt-0.5">共 {{ books.length }} 本书</p>
      </div>
      <div class="flex gap-2">
        <n-button size="small" :loading="scanning" @click="rescan">重新扫描</n-button>
        <n-button size="small" quaternary @click="router.push('/libraries')">
          返回书库
        </n-button>
      </div>
    </div>

    <div v-if="loading" class="text-sm text-neutral-500">加载中…</div>

    <div v-else-if="error" class="rounded-lg border border-red-200 bg-red-50 p-4">
      <p class="text-sm text-red-800">{{ error }}</p>
    </div>

    <!-- 空状态：书库添加了但还没扫描 -->
    <div
      v-else-if="books.length === 0"
      class="rounded-lg border border-neutral-200 p-8 text-center"
    >
      <p class="text-sm text-neutral-500 mb-3">这个书库里还没有章节</p>
      <n-button size="small" :loading="scanning" @click="rescan">扫描书库</n-button>
      <p class="text-xs text-neutral-400 mt-3">
        扫描会遍历整个文件夹，把 .md 识别成章节、人物卡等
      </p>
    </div>

    <n-tree
      v-else
      :data="treeData"
      block-line
      default-expand-all
      @update:selected-keys="handleSelect"
    />
  </div>
</template>
