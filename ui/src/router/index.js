import { createRouter, createWebHistory } from 'vue-router';

const RouteStateView = {
  name: 'RouteStateView',
  render: () => null
};

const routes = [
  {
    path: '/',
    name: 'root',
    component: RouteStateView
  },
  {
    path: '/login',
    name: 'login',
    component: RouteStateView
  },
  {
    path: '/portails',
    name: 'portal-selector',
    component: RouteStateView
  },
  {
    path: '/portail/:portal/:page?',
    name: 'portal-page',
    component: RouteStateView
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  }
});

export default router;
