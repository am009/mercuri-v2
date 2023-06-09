#
# 此脚本执行一个测试
#
# 用法：
# ./script/functional_test_one.sh SOUCE_FILE OUTPUT_FILE
#

BASEDIR=$(realpath $(dirname "$0")/..)
source $BASEDIR/script/common.sh

mkdir -p $BASEDIR/target/test/functional
if [ ! -f $BASEDIR/test/lib/sylib.ll ]; then
    clang -S -emit-llvm $BASEDIR/test/lib/sylib.c -o $BASEDIR/test/lib/sylib.ll
fi

set -e; # error exit

file=$BASEDIR/test/functional/${1}

runone_ir functional $file

runone_asm functional $file
