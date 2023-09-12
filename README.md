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
  --network host \
  -v `pwd`/application.yml:/opt/gb28181-docking-platform-mocking/application.yml \
  skcks.cn/gb28181-docking-platform-mocking:0.0.1-SNAPSHOT
```

### 打包到本地私仓
```shell
mvn deploy -DaltDeploymentRepository=amleixun-mvn-reop::default::file:H:/Repository/skcks.cn/gb28181-docking-platform-mvn-repo
```
git push 推送即可
