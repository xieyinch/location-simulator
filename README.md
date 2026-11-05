# location-simulator

基于 [ZCShou/GoGoGo](https://github.com/ZCShou/GoGoGo) 公开源码进行二次开发，基线提交为 96139eee5e2e174901464d42eeaf67b00f9229f4。源代码中的应用版本为 1.12.3，并非 VirtGO 2.5.2。

当前包含位置收藏及搜索范围的初步修改；尚未完成构建、百度地图搜索或真机定位验证。搜索故障原因尚待排查。

构建需配置百度地图开发者 Key 与安全码，匹配应用包名和签名；请通过本地配置或 GitHub Secrets 保存，不要提交密钥。

原项目说明见 [UPSTREAM_README.md](UPSTREAM_README.md)，本项目遵循 [GPL-3.0-only](LICENSE)。
