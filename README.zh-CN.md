[English](README.md) | 简体中文

# 并发健康检查器

开售前，值班工程师可从一个每行一个 URL 的文件同时检查多个内部 HTTP endpoint。表格按最慢优先排序，显示 URL、HTTP 状态或错误、毫秒延迟和总耗时。每个请求及整次运行都有时间预算。

安装 Java 21。在仓库根目录运行：

```sh
./mvnw -q verify
./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.health.DemoMain
./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.health.HealthMain -Dexec.args='urls.txt virtual 8 1000 3000'
```

本地演示会输出 200、500、TIMEOUT 和 `TOTAL_MS`；端口及耗时会变化。实际运行时创建每行一个 endpoint 的 `urls.txt`。少量 endpoint 适合简单的 `sequential`；`fixed` 限制平台线程；`virtual` 适合扩展阻塞调用，同时用 semaphore 限制并发请求；`async` 组合 `sendAsync`、`orTimeout` 和完成处理器，不为每个等待请求占用一个线程。该 spike 用于开售前值班检查，也为后续票务服务的 timeout 设计提供依据。

## 许可证

本仓库采用 [MIT License](LICENSE)。

## 关于本仓库

本仓库包含一项针对 Stubline 的独立实验。README 说明运行命令与观察结果。

本仓库的代码与文档借助 AI 工具编写；记录的输出都来自实际运行。

## 结论

实验结果及其对后端决策的影响见[FINDINGS.zh-CN.md](FINDINGS.zh-CN.md)。
