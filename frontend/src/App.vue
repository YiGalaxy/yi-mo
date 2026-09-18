<script setup lang="ts">
import { RouterView } from 'vue-router'
import { NMessageProvider } from 'naive-ui'
</script>

<template>
  <!--
    NMessageProvider 必须包在最外层。
    Naive UI 的 useMessage() 需要一个 provider 提供上下文，
    没有的话组件一渲染就抛 "No outer <n-message-provider />" 并白屏。
  -->
  <n-message-provider>
    <RouterView />
  </n-message-provider>
</template>
