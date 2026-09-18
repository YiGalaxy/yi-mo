<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  NButton,
  NCard,
  NEmpty,
  NInput,
  NInputGroup,
  NList,
  NListItem,
  NModal,
  useMessage,
} from 'naive-ui'
import { libraryApi, type Library } from '@/api/library'
import { systemApi } from '@/api/system'
import { toApiError } from '@/api/http'

const message = useMessage()
const libraries = ref<Library[]>([])
const showDialog = ref(false)
const newPath = ref('')
const submitting = ref(false)

async function load() {
  try {
    libraries.value = await libraryApi.list()
  } catch (e) {
    // catch 的变量类型是 unknown，用 toApiError 做一次安全收窄
    message.error(toApiError(e).message)
  }
}

async function submit() {
  // 校验的是输入框内容，不是列表
  const path = newPath.value.trim()
  if (!path) {
    message.warning('请填写文件夹路径')
    return
  }

  submitting.value = true
  try {
    await libraryApi.create({ path })
    message.success('书库已添加')
    showDialog.value = false
    newPath.value = ''
    await load()
  } catch (e) {
    message.error(toApiError(e).message)
  } finally {
    submitting.value = false
  }
}

/**
 * 弹出后端的原生文件夹选择窗口，选中后把路径填进输入框。
 *
 * 浏览器不能获取用户选择的绝对路径，所以这一步必须走后端。
 */
async function browse() {
  try {
    const res = await systemApi.pickDirectory(newPath.value.trim() || undefined)
    if (res.picked && res.path) {
      newPath.value = res.path
    }
    // 用户点了取消：picked 是 false，什么都不做
  } catch (e) {
    // 没有图形界面的环境（Docker、无头服务器）会返回 PICKER_UNSUPPORTED
    message.warning(toApiError(e).message)
  }
}

async function remove(id: string) {
  try {
    await libraryApi.remove(id)
    message.success('已移除（磁盘文件未删除）')
    await load()
  } catch (e) {
    message.error(toApiError(e).message)
  }
}

onMounted(load)
</script>

<template>
  <div class="p-6 max-w-2xl mx-auto">
    <div class="flex items-center justify-between mb-5">
      <h2 class="text-lg font-medium text-neutral-800">书库</h2>
      <n-button type="primary" size="small" @click="showDialog = true">添加书库</n-button>
    </div>

    <n-empty
      v-if="libraries.length === 0"
      description="还没有书库，添加一个文件夹开始吧"
      class="py-16"
    />

    <n-list v-else bordered>
      <n-list-item v-for="lib in libraries" :key="lib.id">
        <div class="flex items-center justify-between">
          <div class="min-w-0">
            <div class="text-sm font-medium text-neutral-800">{{ lib.name }}</div>
            <div class="text-xs text-neutral-500 font-mono truncate mt-0.5">
              {{ lib.path }}
            </div>
          </div>
          <n-button size="tiny" quaternary @click="remove(lib.id)">移除</n-button>
        </div>
      </n-list-item>
    </n-list>

    <n-modal v-model:show="showDialog">
      <n-card style="width: 520px" title="添加书库" :bordered="false" role="dialog">
        <p class="text-xs text-neutral-500 mb-3">
          填写一个文件夹的完整路径。亿墨会把它当作书库，里面的每个子文件夹是一本书。
        </p>
        <n-input-group>
          <n-input
            v-model:value="newPath"
            placeholder="D:\我的小说"
            @keyup.enter="submit"
          />
          <n-button @click="browse">浏览…</n-button>
        </n-input-group>
        <template #footer>
          <div class="flex justify-end gap-2">
            <n-button size="small" @click="showDialog = false">取消</n-button>
            <n-button
              size="small"
              type="primary"
              :loading="submitting"
              :disabled="!newPath.trim()"
              @click="submit"
            >
              确定
            </n-button>
          </div>
        </template>
      </n-card>
    </n-modal>
  </div>
</template>

<style scoped></style>
