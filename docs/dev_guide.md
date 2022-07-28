# 开发环境

TODO

Java

注意配置`src/main/java`为源码目录


### 自动测试

为了正确编译和测试生成的LLVM IR，需要安装clang gcc-arm-linux-gnueabi binutils-arm-linux-gnueabi

为了在本机上测试生成的arm二进制文件，安装qemu-user libc6-dev-armhf-cross。参考https://gist.github.com/luk6xff/9f8d2520530a823944355e59343eadc1 
