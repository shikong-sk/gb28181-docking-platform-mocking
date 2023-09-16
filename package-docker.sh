#!/bin/bash
# 用于缓存打包过程下载的依赖
mkdir repository
curDir=`pwd`
  if ! test -e ffmpeg/ffmpeg;then
  xz -d ffmpeg/ffmpeg-git-amd64-static.tar.xz
  tar -xvf ffmpeg-git-amd64-static.tar -C ./
  mv ffmpeg-git*-static/* ./
  rm -rf ffmpeg-git*-static
fi

docker run --name maven --rm \
	-v ${curDir}:/usr/src/mymaven \
	-v ${curDir}/repository:/root/.m2/repository \
	-v ${curDir}/settings.xml:/usr/share/maven/ref/settings.xml \
	-v /etc/docker/daemon.json:/etc/docker/daemon.json -v /var/run/docker.sock:/var/run/docker.sock -v /usr/bin/docker:/usr/bin/docker \
	-w /usr/src/mymaven \
	maven:3.9.3-eclipse-temurin-17-alpine \
	mvn clean package -DskipTests -Pdocker
docker save skcks.cn/gb28181-docking-platform-mocking -o gb28181-docking-platform-mocking.image
ls -lh *.image
