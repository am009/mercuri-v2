#
# 此脚本执行一个测试
#
# 用法：
# ./script/performance_test_one.sh SOUCE_FILE OUTPUT_FILE
#

BASEDIR=$(realpath $(dirname "$0")/..)
source $BASEDIR/script/common.sh

mkdir -p $BASEDIR/target/test/performance
if [ ! -f $BASEDIR/test/lib/sylib.ll ]; then
    clang -S -emit-llvm $BASEDIR/test/lib/sylib.c -o $BASEDIR/test/lib/sylib.ll
fi

set -e; # error exit

file=$BASEDIR/test/performance/${1}

runone_ir performance $file

runone_asm performance $file
