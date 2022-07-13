BASEDIR=$(realpath $(dirname "$0")/..)
RED='\033[0;34m'
NC='\033[0m' # No Color

function runone {
    printf "${RED}--- Testing ${1} ---${NC}\n"
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
    name=$(basename $file)
    runone $file $BASEDIR/target/test/functional/${name}.ll
    clang $BASEDIR/test/lib/sylib.ll $BASEDIR/target/test/functional/${name}.ll -o $BASEDIR/target/test/functional/${name}.elf
    if [ ! -f $BASEDIR/test/functional/${name%.*}.in ]; then
        python3 $BASEDIR/script/functional_checker.py $BASEDIR/target/test/functional/${name}.elf $BASEDIR/test/functional/${name%.*}.out
    else
        python3 $BASEDIR/script/functional_checker.py $BASEDIR/target/test/functional/${name}.elf $BASEDIR/test/functional/${name%.*}.out $BASEDIR/test/functional/${name%.*}.in
    fi
done