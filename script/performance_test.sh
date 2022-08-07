#
# 此脚本执行所有测试
#
# 用法：
# ./script/performance_test.sh TEST_START_ID
#


BASEDIR=$(realpath $(dirname "$0")/..)
source $BASEDIR/script/common.sh

mkdir -p $BASEDIR/target/test/performance
if [ ! -f $BASEDIR/test/lib/sylib.ll ]; then
    clang -S -emit-llvm $BASEDIR/test/lib/sylib.c -o $BASEDIR/test/lib/sylib.ll
fi

set -e; # error exit

for file in $BASEDIR/test/performance/*.sy; do
    # 性能测试后面很多文件名没有带01_这样的标号，因此不支持从第n个开始

    # runone_ir performance $file

    # 把运行时间放到一个单独的文件，方便对比
    runone_asm performance $file 2>> ./performance_log
done