import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/libraries',
      name: 'libraries',
      // 懒加载：只有访问这个路由时才去下载对应的代码块。
      // 首页不 import 它，首屏就少加载一个组件
      component: () => import('@/views/LibraryView.vue'),
    },
    {
      // :libraryId 是路径参数，页面里用 route.params.libraryId 取
      path: '/libraries/:libraryId/tree',
      name: 'tree',
      component: () => import('@/views/TreeView.vue'),
    },
  ],
})

export default router
