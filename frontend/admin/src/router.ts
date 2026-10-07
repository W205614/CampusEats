import { createRouter, createWebHistory } from 'vue-router';
export const router = createRouter({
  history: createWebHistory('/admin/'),
  routes: [
    { path: '/', component: () => import('./pages/Dashboard.vue') },
    { path: '/orders', component: () => import('./pages/Orders.vue') },
    { path: '/catalog/:kind', component: () => import('./pages/Catalog.vue') },
    { path: '/operations/:kind', component: () => import('./pages/Operations.vue') },
    { path: '/reports', component: () => import('./pages/Reports.vue') },
    { path: '/password', component: () => import('./pages/Password.vue') },
  ],
});
