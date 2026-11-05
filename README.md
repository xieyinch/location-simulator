# location-simulator

基于 [ZCShou/GoGoGo](https://github.com/ZCShou/GoGoGo) 公开源码二次开发，基线提交为 96139eee5e2e174901464d42eeaf67b00f9229f4，源代码应用版本为 1.12.3。

主地图搜索使用 [Photon](https://github.com/komoot/photon) 公共服务，地点数据来自 [OpenStreetMap 及其贡献者](https://www.openstreetmap.org/copyright)。按键盘搜索键提交关键词；公开服务可能限流，地点覆盖和网络可用性因地区而异。收藏功能支持保存选中位置、点击传送和长按删除。上述修改尚需真机验证。

当前底图、悬浮窗地图和相关定位组件仍使用百度地图 SDK，需要符合包名和签名要求的百度 Key；项目还未实现完全免 Key。不要将密钥提交到仓库。编译检查中的占位符不能用于实际地图服务。

原项目说明见 [UPSTREAM_README.md](UPSTREAM_README.md)，遵循 [GPL-3.0-only](LICENSE)。
