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
import {formatDate} from '@vueuse/core';
import {ElNotification} from 'element-plus';
import {h} from 'vue';
import { useI18n } from 'vue-i18n';

function nullOrUndefined(v: any) {
  return v === null || v === undefined;
}

export function prettySize(size: number): string {
  if (nullOrUndefined(size)) return '';

  if (size <= 1024) return size.toLocaleString() + ' B';
  let k = 1024;
  let suffix = ['K', 'M', 'G'];
  let i = Math.min(suffix.length, Math.floor(Math.log(size) / Math.log(k)));
  let res = Math.round((size / Math.pow(k, i)) * 100) / 100.0;
  return res.toLocaleString() + ' ' + suffix[i - 1];
}

export function prettyCount(count: number): string {
  if (nullOrUndefined(count)) return '';
  if (count <= 1000) return count.toLocaleString();

  let k = 1000;
  let suffix = ['k', 'm', 'g', 't', 'p', 'e', 'z', 'y'];
  let i = Math.min(suffix.length, Math.floor(Math.log(count) / Math.log(k)));
  let res = Math.round((count / Math.pow(k, i)) * 100) / 100.0;
  return res.toLocaleString() + ' ' + suffix[i - 1];
}

export function prettyDate(date: number): string {
  return formatDate(new Date(date), 'YYYY-MM-DD HH:mm:ss');
}

export function prettyPercentage(percent: number) {
  return (percent * 100).toFixed(2) + '%';
}

export function makeFirstLetterLowercase(str: string) {
  let sc = str.charAt(1);
  if (sc.toUpperCase() == sc) {
    return str;
  }
  return str.charAt(0).toLowerCase() + str.slice(1);
}

function prettyTimeNumber(n: number) {
  let str = n.toString();
  return str[1] ? str : '0' + n;
}

export function prettyTime(number: number, format: string) {
  let time = new Date(number);
  let newArr = [];
  let formatArr = ['Y', 'M', 'D', 'h', 'm', 's'];
  newArr.push(time.getFullYear().toString());
  newArr.push(prettyTimeNumber(time.getMonth() + 1));
  newArr.push(prettyTimeNumber(time.getDate()));

  newArr.push(prettyTimeNumber(time.getHours()));
  newArr.push(prettyTimeNumber(time.getMinutes()));
  newArr.push(prettyTimeNumber(time.getSeconds()));

  for (let i in newArr) {
    format = format.replace(formatArr[i], newArr[i]);
  }
  return format;
}

let hasUnclosedError = false;

export function showErrorNotification(errorCode: string, message: string) {
  if (hasUnclosedError) {
    return;
  }
  hasUnclosedError = true;
  
  ElNotification.error({
    title: errorCode,
    message: h('p', { style: 'word-break: break-all' }, message),
    offset: 50,
    duration: 0,
    showClose: true,
    onClose() {
      hasUnclosedError = false;
    }
  });
}
