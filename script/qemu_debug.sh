#
# 此脚本运行 arm 虚拟机
#
# 用法：
# ./script/functional_test_one.sh FILE
#
# -g 参数指定调试端口

#!/bin/bash
echo "dont forget to redirect stdin"
qemu-arm -L /usr/arm-linux-gnueabihf/ -g 12345 $1