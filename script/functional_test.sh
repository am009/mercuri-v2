#
# 此脚本执行所有测试
#
# 用法：
# ./script/functional_test.sh TEST_START_ID
#


BASEDIR=$(realpath $(dirname "$0")/..)
RED='\033[0;34m'
NC='\033[0m' # No Color

function runone {
    java -DlogLevel=trace \
        -jar $BASEDIR/target/compiler.jar \
            -S -o $2 \
            $1
}

mkdir -p $BASEDIR/target/test/functional
if [ ! -f $BASEDIR/test/lib/sylib.ll ]; then
    clang -S -emit-llvm $BASEDIR/test/lib/sylib.c -o $BASEDIR/test/lib/sylib.ll
fi

set -e; # error exit




for file in $BASEDIR/test/functional/*.sy; do
    name_=$(basename $file)
    beforeIfs=$IFS
    IFS='_' array=($name_)
    IFS=$beforeIfs

    if [ ${array[0]} -lt $1 ] ; then
        echo "skip ${array[0]}"
        continue
    fi
    
    name=$(basename $file)
    
    printf "${RED}--- Compile IR ${file} ---${NC}\n"
    runone $file $BASEDIR/target/test/functional/${name}.ll
    clang $BASEDIR/test/lib/sylib.ll $BASEDIR/target/test/functional/${name}.ll -o $BASEDIR/target/test/functional/${name}.elf
    printf "${RED}--- Testing IR ${file} ---${NC}\n"
    if [ ! -f $BASEDIR/test/functional/${name%.*}.in ]; then
        python3 $BASEDIR/script/functional_checker.py ir $BASEDIR/target/test/functional/${name}.elf $BASEDIR/test/functional/${name%.*}.out
    else
        python3 $BASEDIR/script/functional_checker.py ir $BASEDIR/target/test/functional/${name}.elf $BASEDIR/test/functional/${name%.*}.out $BASEDIR/test/functional/${name%.*}.in
    fi
    printf "${RED}--- Compile ASM ${file} ---${NC}\n"
    runone $file $BASEDIR/target/test/functional/${name}.S
    arm-linux-gnueabihf-gcc -march=armv7-a -mfpu=vfpv3 -static $BASEDIR/target/test/functional/${name}.S $BASEDIR/test/lib/libsysy.a -o $BASEDIR/target/test/functional/${name}.arm.elf
    printf "${RED}--- Testing ASM ${file} ---${NC}\n"
    if [ ! -f $BASEDIR/test/functional/${name%.*}.in ]; then
        python3 $BASEDIR/script/functional_checker.py asm $BASEDIR/target/test/functional/${name}.arm.elf $BASEDIR/test/functional/${name%.*}.out
    else
        python3 $BASEDIR/script/functional_checker.py asm $BASEDIR/target/test/functional/${name}.arm.elf $BASEDIR/test/functional/${name%.*}.out $BASEDIR/test/functional/${name%.*}.in
    fi
done