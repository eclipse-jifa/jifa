/********************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
// @ts-ignore
import Analysis from '@/components/Analysis.vue';
// @ts-ignore
import Files from '@/components/Files.vue';
// @ts-ignore
import NotFound from '@/components/NotFound.vue';
// @ts-ignore
import ThreadDumpCompare from '@/components/threaddump/ThreadDumpCompare.vue';
import { FileType, fileTypeMap } from '@/composables/file-types';
// @ts-ignore
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

NProgress.configure({
  easing: 'ease',
  speed: 800,
  trickleSpeed: 200,
  minimum: 0.2,
  showSpinner: false
});

declare module 'vue-router' {
  interface RouteMeta {
    fileType?: FileType;
  }
}

const routes: RouteRecordRaw[] = [
  { name: 'Files', path: '/', component: Files },
  { name: 'ThreadDumpCompare', path: '/thread-dump-compare', component: ThreadDumpCompare },
  { name: 'NotFound', path: '/:pathMatch(.*)', component: NotFound }
];

fileTypeMap.forEach((fileType) => {
  routes.push({
    path: `/${fileType.routePath}/:target`,
    component: Analysis,
    meta: { fileType: fileType },
    props: true
  });
});

const router = createRouter({
  history: createWebHistory(),
  routes,
  strict: true
});

router.beforeEach((to, from, next) => {
  if (from.path !== to.path) {
    NProgress.start();
  }
  next();
});

router.afterEach(() => {
  if (NProgress.isStarted()) {
    NProgress.done();
  }
});

export default router;
