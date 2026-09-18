import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 开发时把 /api 转发到后端，浏览器看到的是同源请求，没有跨域问题。
      // 生产环境前端产物打进 jar，前后端本来就同源，这段配置不影响生产。
      //
      // 前端代码里所有请求都写相对路径 '/api/xxx'，
      // 绝对不要写死 http://127.0.0.1:18080 —— 那会导致生产环境全部 404。
      '/api': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
    },
  },
})
