import type { RouteRecordRaw } from 'vue-router'

// 声明路由meta类型
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
    permissions?: string[]
    icon?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { title: '注册', public: true },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' },
      },
      {
        path: 'knowledge-bases',
        name: 'KnowledgeBases',
        component: () => import('@/views/knowledge/KnowledgeBaseList.vue'),
        meta: { title: '知识库管理', permissions: ['KB:VIEW'], icon: 'Collection' },
      },
      {
        path: 'documents',
        name: 'Documents',
        component: () => import('@/views/documents/DocumentList.vue'),
        meta: { title: '文档列表', permissions: ['DOCUMENT:VIEW'], icon: 'Document' },
      },
      {
        path: 'documents/upload',
        name: 'DocumentUpload',
        component: () => import('@/views/documents/DocumentUpload.vue'),
        meta: { title: '上传文档', permissions: ['DOCUMENT:UPLOAD'], icon: 'Upload' },
      },
      {
        path: 'documents/:id/content',
        name: 'DocumentContent',
        component: () => import('@/views/documents/DocumentContent.vue'),
        meta: { title: '文档浏览', permissions: ['DOCUMENT:VIEW'], icon: 'Reading' },
      },
      {
        path: 'documents/:id/chunks',
        name: 'ChunkReview',
        component: () => import('@/views/documents/ChunkReview.vue'),
        meta: { title: '块审核', permissions: ['DOCUMENT:VIEW'], icon: 'Grid' },
      },
      {
        path: 'chunks/:chunkId/detail',
        name: 'ChunkDetail',
        component: () => import('@/views/documents/ChunkDetail.vue'),
        meta: { title: '块详情', permissions: ['DOCUMENT:VIEW'], icon: 'Reading' },
      },
      {
        path: 'documents/:id/raw-view',
        name: 'RawFileView',
        component: () => import('@/views/documents/RawFileView.vue'),
        meta: { title: '原文查看', permissions: ['DOCUMENT:VIEW'], icon: 'Reading' },
      },
      {
        path: 'review',
        name: 'Review',
        component: () => import('@/views/review/ReviewList.vue'),
        meta: { title: '审核管理', permissions: ['REVIEW:VIEW'], icon: 'Checked' },
      },
      {
        path: 'qa',
        name: 'QA',
        component: () => import('@/views/qa/QAChat.vue'),
        meta: { title: '知识问答', permissions: ['QA:ASK'], icon: 'ChatDotRound' },
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/users/UserList.vue'),
        meta: { title: '用户管理', permissions: ['USER:VIEW'], icon: 'User' },
      },
      {
        path: 'configs',
        name: 'SystemConfig',
        component: () => import('@/views/config/SystemConfig.vue'),
        meta: { title: '系统配置', permissions: ['CONFIG:MANAGE'], icon: 'Setting' },
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/feedback/FeedbackList.vue'),
        meta: { title: '反馈管理', permissions: ['FEEDBACK:VIEW'], icon: 'ChatLineSquare' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/Profile.vue'),
        meta: { title: '个人中心', icon: 'UserFilled' },
      },
    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', public: true },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
  },
]

export default routes
