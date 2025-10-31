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
import heapDump from './heapdump/en'
import gclog from './gclog/en'
import threadDump from './threaddump/en'
import jfr from '@/i18n/jfr/en'

export default {
  jifa: {
    common: {
      confirm: 'Confirm',
      cancel: 'Cancel',
      submit: 'Submit',
      back: 'Back',
      operations: 'Operations',
      copy: 'Copy',
      search: 'Search',
      result: 'Result',
      description: 'Description',
      detail: 'Detail',
      noData: 'No Data',
      file: 'File',
      messageBeforeLeave: 'Sure to leave?',
      clickToLoadMore: 'Click to load more data',
      expandedColumnWidth: '+ Width',
      comma: ', '
    },

    header: {
      login: 'Login',
      logout: 'Logout',

      download: 'Download'
    },

    menu: {
      files: "Files",
      analysisView: "Analysis View",
    },

    serviceUnavailable: {
      title: 'Service is unavailable',
      subtitle: 'Please contact the administrator'
    },

    file: {
      name: 'Filename',
      type: 'Type',
      size: 'Size',
      uploadedTime: 'Uploaded Time',
      operations: 'Operations',

      all: 'All Types',
      heapDump: 'Heap Dump',
      GCLog: 'GC Log',
      threadDump: 'Thread Dump',
      jfr: 'JFR File',

      new: 'New File',

      analyze: 'Analyze'
    },

    form: {
      requiredMessage: "Please enter '{0}'",
      selectionRequiredMessage: "Please select '{0}'",
      invalidLengthMessage: "Length of '{0}' should be {1} to {2}"
    },

    loginForm: {
      login: 'Log in',
      loginTitle: 'Hi, Log in to your Account',
      loginSubtitle: "Don't have an account yet?",
      signup: 'Sign up',
      signupTitle: 'Register a new Account',
      signupSubtitle: 'Already have an account?',
      usernameLabel: 'Username',
      usernamePlaceholder: "Enter your username, e.g. grace{'@'}gmail.com",
      passwordLabel: 'Password',
      passwordPlaceholder: 'Enter your password',
      fullNameLabel: 'Full Name',
      fullNamePlaceholder: 'Enter your name',
      emailLabel: 'Email',
      usedAsUsername: ' (will be used as Username)',
      emailPlaceholder: "Enter your email, e.g. e.g. grace{'@'}gmail.com",
      or: 'or',
      continueWith: 'Continue with {0}',
      invalidEmailMessage: 'Please enter a valid Email'
    },

    fileTransferForm: {
      transferMethod: 'Method',
      type: 'Type',

      upload: 'Upload',
      filename: 'File Name',
      text: 'Text',

      dragOrClickToUpload: 'Drop file here or click to upload',
      fileSizeLimit: 'File size limit: {size}',
      fileSizeExceeded: 'File too large! File size {fileSize} exceeds limit {maxSize}',
      uploadFailed: 'File upload failed',
      fileCountLimit: 'Only one file can be uploaded',

      host: 'Host',
      user: 'User',
      authentication: 'Authentication',
      password: 'Password',
      publicKey: 'Public Key',
      path: 'Path',

      publicKeyCopyPrompt: 'Please copy key to ~/.ssh/authorized_keys'
    },

    analysis: {
      log: 'Log',
      success: 'Analysis successful',
      comparison: 'Comparison',
      selectComparisonTargets: 'Select Targets',
      setting: 'Setting',

      waitingForResourceScheduling: 'Waiting for analysis resource scheduling',
    },



    heapDump,
    gclog,
    threadDump,
    jfr,
  }
};
