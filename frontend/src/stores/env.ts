/********************************************************************************
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation
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
import { useAnalysisStore } from '@/stores/analysis';
import { showErrorNotification } from '@/support/utils';
import axios from 'axios';
import axiosRetry from 'axios-retry';
import { defineStore } from 'pinia';
// @ts-ignore
import Cookies from 'js-cookie';

export interface User {
  name: string;
  admin: boolean;
}

export interface PublicKey {
  pkcs8: string;
  ssh2: string;
}

export interface HandshakeResponse {
  serverRole: string;
  allowLogin: boolean;
  allowAnonymousAccess: boolean;
  allowRegistration: boolean;
  publicKey: PublicKey;
  oauth2LoginLinks: object;
  user?: User;
  disabledFileTransferMethods: [];
  maxFileSize: number;
}

const tokenKey = 'jifa-token';

function getToken() {
  return window.localStorage.getItem(tokenKey) || Cookies.get(tokenKey);
}

function goHome() {
  useAnalysisStore().leaveGuard = false;
  location.assign('/');
}

export const useEnv = defineStore('env', {
  state: () => ({
    serverRole: null as string | null,
    allowLogin: false,
    allowAnonymousAccess: false,
    allowRegistration: false,
    oauth2LoginLinks: null as object | null,

    loginFormVisible: false,

    user: null as User | null,
    publicKey: null as PublicKey | null,
    uploadHeader: {}, // used by upload

    disabledFileTransferMethods: [],
    maxFileSize: Infinity // Unlimited size by default
  }),

  getters: {
    loggedIn: (state) => state.user != null,
    loginRequired: (state) => !state.user && !state.allowAnonymousAccess,
    supportedOauth2Login: (state) =>
      state.oauth2LoginLinks && Object.keys(state.oauth2LoginLinks).length > 0,
    token: () => getToken()
  },

  actions: {
    init() {
      let token = getToken();
      if (token) {
        token = 'Bearer ' + token;
        axios.defaults.headers.common['Authorization'] = token;
        this.uploadHeader = { Authorization: token };
      }
    },

    handleHandshakeData(data: HandshakeResponse) {
      this.serverRole = data.serverRole;
      this.allowLogin = data.allowLogin;
      this.allowAnonymousAccess = data.allowAnonymousAccess;
      this.allowRegistration = data.allowRegistration;
      this.oauth2LoginLinks = data.oauth2LoginLinks;
      this.publicKey = data.publicKey;
      // Use the actual server configuration
      this.maxFileSize = data.maxFileSize || Infinity;
      if (data.user) {
        this.user = data.user;
      } else if (!this.allowAnonymousAccess) {
        this.loginFormVisible = true;
      }

      this.disabledFileTransferMethods = data.disabledFileTransferMethods;
    },

    logout() {
      Cookies.remove(tokenKey);
      window.localStorage.removeItem(tokenKey);
      goHome();
    },

    handleLoginOrSignupResponse(resp: any) {
      window.localStorage.setItem(tokenKey, resp.headers.authorization);
      goHome();
    },

    resetToken() {
      window.localStorage.removeItem(tokenKey);
      Cookies.remove(tokenKey);
    },

    isCluster() {
      return this.serverRole === 'MASTER';
    }
  }
});

axiosRetry(axios, {
  retries: 60,
  shouldResetTimeout: true,
  retryDelay: (retryCount) => {
    return 2000;
  },
  retryCondition: (error) => {
    let resp = error.response;
    if (resp) {
      let status = resp.status;
      let data: any = resp.data;
      if (status === 500) {
        if (data && data.hasOwnProperty('errorCode')) {
          if (data.errorCode === 'ELASTIC_WORKER_NOT_READY') {
            return true;
          }
        }
      }
    }
    return false;
  }
});

axios.interceptors.response.use(
  function (response) {
    return response;
  },
  function (error) {
    let resp = error.response;
    if (resp) {
      let status = resp.status;
      let data = resp.data;
      if (status === 500) {
        if (data && data.hasOwnProperty('errorCode')) {
          showErrorNotification(data.errorCode, data.message);
        }
      }
    }
    return Promise.reject(error);
  }
);
