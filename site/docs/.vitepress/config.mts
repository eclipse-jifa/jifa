import {defineConfig} from 'vitepress'

export default defineConfig({
  base: '/jifa/',

  title: "Eclipse Jifa",
  titleTemplate: ":title · Eclipse Jifa",
  description: "An open-source project for diagnosing common Java issues.",

  lastUpdated: true,

  head: [
    ['link', {rel: "icon", href: "/jifa/eclipse_incubation_vertical_svg.svg"}],
  ],


  themeConfig: {
    search: {
      provider: 'local'
    },

    socialLinks: [
      {icon: 'github', link: 'https://github.com/eclipse/jifa'}
    ],
  },

  locales: {
    root: {
      label: 'English',
      lang: 'en',

      themeConfig: {
        nav: [
          {text: 'Home', link: '/'},
          {text: 'Guide', link: '/guide/what-is-eclipse-jifa'},
          {
            text: '0.3.0-SNAPSHOT',
            items: [
              {
                text: 'Changelog',
                link: '/guide/changelog.md'
              },
              {
                text: 'Contributing',
                link: '/guide/contributing'
              }
            ]
          }
        ],

        sidebar: [
          {
            text: 'Introduction',
            items: [
              {text: 'What is Eclipse Jifa?', link: '/guide/what-is-eclipse-jifa'},
              {text: 'Getting Started', link: '/guide/getting-started'},
            ]
          },
          {
            text: 'Core Features',
            items: [
              {text: 'Heap Dump Analysis', link: '/guide/heap-dump-analysis'},
              {text: 'GC Log Analysis', link: '/guide/gc-log-analysis'},
              {text: 'Thread Dump Analysis', link: '/guide/thread-dump-analysis'},
              {text: 'JFR Analysis', link: '/guide/jfr-analysis'},
            ]
          },
          {
            items: [
              {text: 'Deployment', link: '/guide/deployment.md'},
              {text: 'Configuration', link: '/guide/configuration.md'},
            ]
          },
          {
            text: 'Integrations',
            items: [
              {text: 'MCP (Experimental)', link: '/guide/mcp'},
            ]
          },
          {
            items: [
              {text: 'Changelog', link: '/guide/changelog.md'},
              {text: 'Contributing', link: '/guide/contributing.md'},
            ]
          }
        ],

        outline: {
          level: [2, 3],
        },

        editLink: {
          text: 'Edit this page on GitHub',
          pattern: 'https://github.com/eclipse/jifa/edit/main/site/docs/:path'
        },

        lastUpdated: {
          text: 'Updated at',
          formatOptions: {
            dateStyle: 'short',
            timeStyle: 'short',
            forceLocale: true
          }
        }
      },
    },

    zh: {
      label: '简体中文',
      lang: 'zh',

      themeConfig: {
        nav: [
          {text: '主页', link: '/zh/'},
          {text: '指南', link: '/zh/guide/what-is-eclipse-jifa'},
          {
            text: '0.3.0-SNAPSHOT',
            items: [
              {
                text: '更新日志',
                link: '/zh/guide/changelog.md'
              },
              {
                text: '贡献指南',
                link: '/zh/guide/contributing'
              }
            ]
          }
        ],

        sidebar: [
          {
            text: '介绍',
            items: [
              {text: 'Eclipse Jifa 是什么？', link: '/zh/guide/what-is-eclipse-jifa'},
              {text: '快速上手', link: '/zh/guide/getting-started'},
            ]
          },
          {
            text: '核心功能介绍',
            items: [
              {text: '堆快照分析', link: '/zh/guide/heap-dump-analysis'},
              {text: 'GC 日志分析', link: '/zh/guide/gc-log-analysis'},
              {text: '线程快照分析', link: '/zh/guide/thread-dump-analysis'},
              {text: 'JFR 分析', link: '/zh/guide/jfr-analysis'},
            ]
          },
          {
            items: [
              {text: '部署', link: '/zh/guide/deployment.md'},
              {text: '配置', link: '/zh/guide/configuration.md'},
            ]
          },
          {
            text: '集成',
            items: [
              {text: 'MCP（实验性）', link: '/zh/guide/mcp'},
            ]
          },
          {
            items: [
              {text: '更新日志', link: '/zh/guide/changelog.md'},
              {text: '贡献指南', link: '/zh/guide/contributing.md'},
            ]
          }
        ],

        outline: {
          label: '本页目录',
          level: [2, 3],
        },

        editLink: {
          text: '在 GitHub 上编辑此页',
          pattern: 'https://github.com/eclipse/jifa/edit/main/site/docs/:path'
        },

        lastUpdated: {
          text: '更新于',
          formatOptions: {
            dateStyle: 'short',
            timeStyle: 'short',
            forceLocale: true
          }
        }
      },
    }
  },
})
