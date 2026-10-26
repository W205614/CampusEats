import { createRouter, createWebHistory } from 'vue-router';
import { useSession } from './stores/session';
export const homeFor = (role: string) => role === 'DELIVERER' || role === 'OPERATOR' ? '/orders' : '/';
export const router = createRouter({
  history: createWebHistory('/admin/'),
  routes: [
    { path: '/', component: () => import('./pages/Dashboard.vue') },
    { path: '/catalog', component: () => import('./pages/Catalog.vue') },
    { path: '/marketing', component: () => import('./pages/Marketing.vue') },
    { path: '/orders', component: () => import('./pages/Orders.vue') },
    { path: '/operations/:kind', component: () => import('./pages/Operations.vue') },
    { path: '/reports', component: () => import('./pages/Reports.vue') },
    { path: '/users', component: () => import('./pages/Users.vue') },
    { path: '/finance', component: () => import('./pages/Finance.vue') },
    { path: '/service-operations', component: () => import('./pages/ServiceOperations.vue') },
    { path: '/messages', component: () => import('./pages/Inbox.vue') },
    { path: '/password', component: () => import('./pages/Password.vue') },
  ],
});
router.beforeEach((to) => {
  const session = useSession().session;
  if (!session) return true;
  if (session.mustChangePassword && to.path !== '/password') return '/password';
  if (to.path === '/password') return true;
  if (session.role === 'DELIVERER' && !['/orders','/messages'].includes(to.path)) return '/orders';
  if (session.role === 'OPERATOR' && !['/', '/orders', '/operations/aftersales', '/operations/reviews','/messages','/service-operations'].includes(to.path)) return '/orders';
  if (to.path === '/users' && session.role !== 'PLATFORM_ADMIN') return '/';
  if (session.role === 'SHOP_MANAGER' && to.path === '/operations/buildings') return '/';
  return true;
});
