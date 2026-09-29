# LX Music Mobile 参考与轻听实现映射

2026-09-16 只读克隆参考仓库，commit `cd37a979a5845f1220b306b374285f5d38329e8d`。未运行参考项目、未复制它的业务代码进 APK。

| 参考内容 | 上游文件 | 轻听处理 |
| --- | --- | --- |
| 网址导入自定义音源 | src/screens/Home/Views/Setting/settings/Basic/UserApiEditModal/ScriptImportOnline.tsx | 保留 MusicFree JSON/JS 订阅；不新增 LX 脚本执行器 |
| 榜单与分页 | src/core/leaderboard.ts | Charts.java 保存来源/分组/原参数、去重分页；MusicRepository 使用现有插件方法 |
| 滚动、拖动暂停与 3 秒恢复 | src/screens/PlayDetail/Vertical/Lyric.tsx | LyricFollow.java + LyricsView.java，按播放器实际时间居中；暂停状态也能回到当前 |
| 指定句子播放 | src/screens/PlayDetail/components/PlayLine.tsx | 点击歌词句子 seekTo；检查文档身份防旧歌词跳转新歌曲 |
| 播放与歌词事件 | src/core/init/player/lyric.ts | 高亮、进度和底部当前句共用 PlaybackService 的位置与歌词偏移 |

轻听 API 依据 [MusicFree 官方协议](https://musicfree.catcat.work/plugin/protocol.html)：getTopLists 返回分组数组；getTopListDetail 接收完整榜单对象和从 1 开始的页码，返回 musicList/isEnd/topListItem。isEnd 缺省为 true。

本次再次只下载用户订阅中的 W音乐、酷狗脚本文本，两者均 HTTP 200 且含 getTopLists/getTopListDetail 方法名。此结果仅确认静态接口线索，不代表真实排行榜 API 或音频已经实测可用。旧版储存的脚本以实际运行 metadata 返回能力为准。

## 复用验证
ChartsTest 覆盖分组/原参数、旧请求失效、分页去重/结束、补充榜单字段；LyricFollowTest 覆盖按住、释放超时和显式恢复；LyricsTest 覆盖同步偏移与反向 seek；fixture 提供两个榜单与两页歌曲。README 有连接测试设备后的运行命令。
