#
# 此脚本执行一个测试
#
# 用法：
# ./script/functional_test_one.sh SOUCE_FILENAME [hidden]
#
# 测试hidden_functional用例的时候执行 ./script/functional_test_one.sh xxx.sy hidden
# 例如：./script/functional_test_one.sh 04_break_continue.sy hidden

BASEDIR=$(realpath $(dirname "$0")/..)
source $BASEDIR/script/common.sh

mode=functional
if [ "$2" = "hidden" ] ; then
    mode=hidden_functional
fi

mkdir -p $BASEDIR/target/test/${mode}
if [ ! -f $BASEDIR/test/lib/sylib.ll ]; then
    clang -S -emit-llvm $BASEDIR/test/lib/sylib.c -o $BASEDIR/test/lib/sylib.ll
fi

set -e; # error exit

file=$BASEDIR/test/${mode}/${1}

runone_ir ${mode} $file

runone_asm ${mode} $file
