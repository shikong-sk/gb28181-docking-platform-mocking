FROM eclipse-temurin:17-jre-focal

MAINTAINER Shikong <919411476@qq.com>

ENV HOME_PATH /opt/gb28181-docking-platform-mocking/
RUN mkdir -p $HOME_PATH
WORKDIR $HOME_PATH

ADD target/gb28181-mocking-starter.jar /opt/gb28181-docking-platform-mocking/starter.jar

EXPOSE 18182

ENTRYPOINT ["java", "-jar","/opt/gb28181-docking-platform-mocking/starter.jar"]
