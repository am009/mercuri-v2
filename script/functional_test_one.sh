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

file=$BASEDIR/test/functional/${1}
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
arm-linux-gnueabihf-gcc -march=armv7 $BASEDIR/test/lib/libsysy.a $BASEDIR/target/test/functional/${name}.S -o $BASEDIR/target/test/functional/${name}.arm.elf
if [ ! -f $BASEDIR/test/functional/${name%.*}.in ]; then
    python3 $BASEDIR/script/functional_checker.py asm $BASEDIR/target/test/functional/${name}.arm.elf $BASEDIR/test/functional/${name%.*}.out
else
    python3 $BASEDIR/script/functional_checker.py asm $BASEDIR/target/test/functional/${name}.arm.elf $BASEDIR/test/functional/${name%.*}.out $BASEDIR/test/functional/${name%.*}.in
fi
printf "${RED}--- Testing ASM ${file} ---${NC}\n"
