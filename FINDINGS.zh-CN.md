[English](FINDINGS.md) | 简体中文

# 实验结论

## 观察

本地演示检查返回 200、500 和 TIMEOUT 的端点，并打印总耗时。检查器提供顺序、固定线程、虚拟线程和异步四种模式，同时限制单次请求与整轮运行的时间。虚拟线程模式还用信号量限制同时发出的请求；输出按耗时排序。

## 对后端的影响

开售前值班人员可以并发检查多个端点，避免一个慢端点无限拖住整轮检查。这为限时并发检查和截止时间设计提供依据，不能据此推算生产环境的承载能力。参见[后端 ADR 0003：Server execution strategy](https://github.com/codexary/stubline-backend/blob/main/docs/adr/0003-server-execution-strategy.md)。
