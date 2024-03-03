# gb28181-docking-platform-mocking

gb28181 协议 对接平台 (设备模拟)
gb28181-docking-platform-mocking

### 项目依赖
如果项目依赖无法拉取 请尝试将 maven 配置中
mirror.id maven-default-http-blocker 部分改为
```xml
<mirror>
      <id>maven-default-http-blocker</id>
      <!--<mirrorOf>external:http:*</mirrorOf>-->
      <!--放行 http 协议下载 -->
      <mirrorOf>!*</mirrorOf>
      <name>Pseudo repository to mirror external repositories initially using HTTP.</name>
      <url>http://0.0.0.0/</url>
      <blocked>true</blocked>
    </mirror>
```
### 项目打包
#### 打包 为 jar
```shell
mvn clean package
```
打包后jar在 gb28181-mocking-orm/target/starter.jar

#### 打包 为 docker 镜像
一键脚本(纯docker环境打包 + 编译)
```
chmod +x ./package-docker.sh
./package-docker.sh
```
打包后的 docker镜像文件位于 项目根目录 gb28181-docking-platform-mocking.image

##### 测试运行
```shell
docker run --name gb28181-mocking --rm \
  --log-opt max-size=1g \
  --network host \
  -v `pwd`/record:/tmp/record \
  -v `pwd`/application.yml:/opt/gb28181-docking-platform-mocking/application.yml \
  skcks.cn/gb28181-docking-platform-mocking:0.1.0-SNAPSHOT
```

### 打包到本地私仓
```shell
mvn deploy -DaltDeploymentRepository=amleixun-mvn-reop::default::file:H:/Repository/skcks.cn/gb28181-docking-platform-mvn-repo
```
git push 推送即可

### 关于外置 ffmpeg 部分
项目仓库中自带一个 linux amd64 ffmpeg 用于docker打包

如果有其他平台需要可 修改位于 gb28181-mocking-starter 的 Dockerfile 文件

#### linux
linux 各发行版本可使用对应 官方源 安装
或者根据不同平台使用 https://www.johnvansickle.com/ffmpeg/ 提供的免编译版本
例:

amd64: https://johnvansickle.com/ffmpeg/builds/ffmpeg-git-amd64-static.tar.xz

arm64: https://johnvansickle.com/ffmpeg/builds/ffmpeg-git-arm64-static.tar.xz

#### windows
可到 ffmpeg.org 官网查找

或 到 github https://github.com/BtbN/FFmpeg-Builds/releases

下载对应版本

例:

win64: https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip

### swagger 地址
`/swagger-ui/index.html`

# 虚拟设备注册流程

## 1. 模拟程序添加 设备信息

+ 调用 `/device/add` 添加设备 接口

  ```text
  deviceCode*	string 设备编码
  gbDeviceId*	string 国标编码
  gbChannelId*	string 国标通道id
  name*	string 设备名称
  address	string 地址
  enable	boolean 是否启用
  liveStream	string 实时视频流地址
  ```

  ```json
  {
    "deviceCode": "[设备编码]",
    "gbDeviceId": "[设备国标编码]",
    "gbChannelId": "[设备国标编码/设备国标通道编码]",
    "name": "[设备名称]",
    "address": "[设备地址]",
    "enable": true,
    "liveStream": "[rtsp 实时流地址]"
  }
  ```

  **例:**

  ```json
  {
    "id": 1,
    "deviceCode": "XXXXXXXXXXXXXXXXXXXXX",
    "gbDeviceId": "44000100001110000010",
    "gbChannelId": "44000100001310000010",
    "name": "模拟设备",
    "address": "",
    "enable": true,
    "liveStream": "rtsp://admin:123456@10.10.10.120:554"
  }
  ```

## 2. 当添加完所有设备后 批量注册到 wvp

+ 调用 `/gb28181/register`  设备注册 接口

调用此接口则立即向wvp 批量注册所有 启用的设备


# 关于实时视频地址
+ rtsp 地址格式为:
  `rtsp://[账号]:[密码]@[ip]:[端口]/[码流地址]`


# 关于历史视频

目前历史视频通过 /video 接口 拉取并临时缓存

默认缓存路径为 /tmp/record

因 zlm 本身也有缓存 所以也需要一并清理

目前线上通过 linux 自带 crontab 定时任务自动清理

目前每 30 分钟执行一次

```bash
0 */30 * * * /opt/gb28181/clean.sh
```

clean.sh

```bash
#!/bin/bash
# 清理历史视频缓存
find /tmp/record -mmin +90 -type f | xargs -d '\n' -r readlink -f | xargs -d '\n' -r rm -rf

# 清理 zlm 缓存
find /opt/gb28181/zlm/www/record/ -mmin +90 -type f | xargs -d '\n' -r readlink -f | xargs -d '\n' -r rm -rf
find /opt/gb28181/zlm/www/record/ -type d -empty -delete

```


# 常见问题

+ 如果需要修改设备信息可 调用 `/device/modify/id` 接口修改设备信息,

  如果修改的是 国标id、国标通道编码，修改后需要 重新调用一次 **设备注册** 接口

  如果修改的是 设备名称、设备地址，修改后需要 在 wvp 界面 对应的 国标设备 刷新 更新设备信息


+ 如果实时视频无法播放， 请在本地尝试 使用 vlc 或其他播放器 播放该实时视频

  如果 本地无法播放 请检查设备端口是否开放

  如果 本地可播放 请检查服务器与设备能否通信， 或视频编码是否为 h264

+ 如果历史视频 点播/下载失败, 请尝试能否成功下载

  如果 能成功下载, 请使用 ffprobe 确认视频编码是否为 h264
