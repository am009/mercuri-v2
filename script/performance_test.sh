#
# 此脚本执行所有测试
#
# 用法：
# ./script/performance_test.sh TEST_START_PREFIX
# 例如输入./script/performance_test.sh stencil 直接从前缀为stencil的用例开始
#


BASEDIR=$(realpath $(dirname "$0")/..)
source $BASEDIR/script/common.sh

mkdir -p $BASEDIR/target/test/performance
if [ ! -f $BASEDIR/test/lib/sylib.ll ]; then
    clang -S -emit-llvm $BASEDIR/test/lib/sylib.c -o $BASEDIR/test/lib/sylib.ll
fi

set -e; # error exit

for file in $BASEDIR/test/performance/*.sy; do
    filename=$(basename $file)
    if [[ "$1" > "$filename" ]]; then
        continue;
    fi
    # 性能测试后面很多文件名没有带01_这样的标号，因此不支持从第n个开始

    # runone_ir performance $file

    # TODO 把运行时间放到一个单独的文件，方便对比
    runone_asm performance $file
done