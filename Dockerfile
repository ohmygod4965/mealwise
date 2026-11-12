# =====================================================================
# Mealwise 后端镜像（多阶段）
#   构建阶段：容器内用 Maven 打包，宿主机不需要装 JDK / Maven
#   运行阶段：只带 JRE 17 + 可执行 jar（前端 dist 已在 jar 内，单端口）
#
# 用法：docker compose up --build
# =====================================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build

# 部分网络环境 IPv6 不通：强制 JVM 走 IPv4，避免 Maven 拉依赖时卡住
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"

WORKDIR /src

# 先只拷 pom，依赖层可复用缓存
COPY mealwise-server/pom.xml ./mealwise-server/pom.xml
RUN mvn -B -q -f mealwise-server/pom.xml dependency:go-offline || true

# 再拷源码（src/main/resources/static 里是已构建好的前端产物）
COPY mealwise-server/src ./mealwise-server/src
RUN mvn -B -f mealwise-server/pom.xml -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /src/mealwise-server/target/mealwise-server-1.0.0.jar /app/app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.net.preferIPv4Stack=true"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=prod"]
