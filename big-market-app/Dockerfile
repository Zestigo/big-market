# 基础镜像：匹配你本地的 JDK 1.8
FROM openjdk:8-jre-slim

# 作者信息
LABEL maintainer="cyh"

# 解决日志时间对不上的问题
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 配置环境变量，方便后续动态调整
ENV JAVA_OPTS=""
ENV PARAMS=""

# 将上传的 JAR 包拷贝进镜像
ADD big-market-app.jar /big-market-app.jar

# 启动命令
ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /big-market-app.jar $PARAMS"]