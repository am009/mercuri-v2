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
    name_=$(basename $file)
    beforeIfs=$IFS
    IFS='_' array=($name_)
    IFS=$beforeIfs

    if [ ${array[0]} -lt $1 ] ; then
        echo "skip ${array[0]}"
        continue
    fi
    
    runone_ir performance $file

    runone_asm performance $file
done