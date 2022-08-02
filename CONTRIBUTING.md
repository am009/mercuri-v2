# 贡献指南

## 环境需求

+ Ubuntu 18.04 LTS or higher (建议 22.04)
+ Shell 已配置代理
+ JDK 15

JDK 15 的安装：

```
sudo mkdir -p /usr/lib/jvm/
wget https://download.java.net/java/GA/jdk15/779bf45e88a44cbd9ea6621d33e33db1/36/GPL/openjdk-15_linux-x64_bin.tar.gz -O /tmp/openjdk-15_linux-x64_bin.tar.gz
sudo tar -xzf /tmp/openjdk-15_linux-x64_bin.tar.gz -C /usr/lib/jvm/

sudo update-alternatives --install "/usr/bin/java" "java" "/usr/lib/jvm/jdk-15/bin/java" 0 
sudo update-alternatives --install "/usr/bin/javac" "javac" "/usr/lib/jvm/jdk-15/bin/javac" 0 
sudo update-alternatives --install "/usr/bin/javap" "javap" "/usr/lib/jvm/jdk-15/bin/javap" 0 
sudo update-alternatives --set java /usr/lib/jvm/jdk-15/bin/java 
sudo update-alternatives --set javac /usr/lib/jvm/jdk-15/bin/javac 
sudo update-alternatives --set javap /usr/lib/jvm/jdk-15/bin/javap 

sudo ln -s /usr/lib/jvm/jdk-15/bin/jar /usr/bin/jar
```

检查：

```
java --version
```

## 起步

```
git clone https://github.com/pluveto/mercuri
```

## 安装 Docker

```shell
cd ~
# docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
rm get-docker.sh
# docker compose
sudo curl -L "https://github.com/docker/compose/releases/download/1.29.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
sudo ln -s /usr/local/bin/docker-compose /usr/bin/docker-compose
```

## 项目结构

## 运行

```shell
docker run --rm -it -v ~/repo/mercuri:/coursegrader/submitdata cg/compile_x86:v1.7 bash
```

+ `--rm` 表示如果容器存在，就自动清除
+ `-it` 表示运行容器后，会进入到容器的 bash 终端
+ `-v` 表示将项目的根目录挂载到容器的 `/coursegrader/submitdata` 目录下