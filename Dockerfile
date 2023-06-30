# 빌드 스테이지
FROM gradle:7.6.1-jdk17 AS build
COPY . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon

# 실행 스테이지
FROM mcr.microsoft.com/java/jre:17-zulu-ubuntu

# 빌드 스테이지에서 생성된 JAR 파일 복사
COPY --from=build /home/gradle/src/build/libs/*.jar /app.jar

# Google Chrome 및 ChromeDriver 설치
RUN apt-get update && apt-get install -y \
  libssl-dev \
  libnss3 \
  wget \
  unzip \
  curl && \
  wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb && \
  apt -y install ./google-chrome-stable_current_amd64.deb && \
  wget -O /tmp/chromedriver.zip https://chromedriver.storage.googleapis.com/`curl -sS chromedriver.storage.googleapis.com/LATEST_RELEASE`/chromedriver_linux64.zip && \
  unzip /tmp/chromedriver.zip chromedriver -d /usr/bin && \
  rm -rf /var/lib/apt/lists/* \
  ./google-chrome-stable_current_amd64.deb \
  /tmp/chromedriver.zip

# 포트 노출
EXPOSE 8081

# Java 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app.jar"]
