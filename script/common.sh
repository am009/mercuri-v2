# set BASEDIR before source this file
if [ -z "${BASEDIR}" ]; then echo "BASEDIR is not set!!"; exit 1; fi

RED='\033[0;34m'
NC='\033[0m' # No Color

function compile_one {
    java -DlogLevel=trace \
        -DlogTarget=file \
        -DlogFile=`dirname ${2}`/`basename ${1}`.log \
        -jar $BASEDIR/target/compiler.jar \
            -S -o $2 \
            $1
}

function compile_one_opt {
    java -DlogLevel=trace \
        -DlogTarget=file \
        -DlogFile=`dirname ${2}`/`basename ${1}`.log \
        -jar $BASEDIR/target/compiler.jar \
            -S -o $2 \
            $1 -O2
}

# $1: functional or performance
# $2: full path of target .sy file
function runone_ir {
    category=$1
    file=$2
    name=$(basename $file)
    printf "${RED}--- Compile IR ${file} ---${NC}\n"
    echo $BASEDIR/target/test/${category}/${name}.ll
    echo $BASEDIR/target/test/${category}/${name}.log
    compile_one $file $BASEDIR/target/test/${category}/${name}.ll
    clang -O2 $BASEDIR/test/lib/sylib.ll $BASEDIR/target/test/${category}/${name}.ll -o $BASEDIR/target/test/${category}/${name}.elf
    printf "${RED}--- Testing IR ${file} ---${NC}\n"
    if [ ! -f $BASEDIR/test/${category}/${name%.*}.in ]; then
        python3 $BASEDIR/script/functional_checker.py ir $BASEDIR/target/test/${category}/${name}.elf $BASEDIR/test/${category}/${name%.*}.out
    else
        python3 $BASEDIR/script/functional_checker.py ir $BASEDIR/target/test/${category}/${name}.elf $BASEDIR/test/${category}/${name%.*}.out $BASEDIR/test/${category}/${name%.*}.in
    fi
}

# $1: functional or performance
# $2: full path of target .sy file
function runone_asm {
    category=$1
    file=$2
    name=$(basename $file)
    printf "${RED}--- Compile ASM ${file} ---${NC}\n"
    echo $BASEDIR/target/test/${category}/${name}.S
    echo $BASEDIR/target/test/${category}/${name}.log
    compile_one $file $BASEDIR/target/test/${category}/${name}.S
    arm-linux-gnueabihf-gcc -march=armv7-a -mfpu=vfpv3 -static $BASEDIR/target/test/${category}/${name}.S $BASEDIR/test/lib/libsysy.a -o $BASEDIR/target/test/${category}/${name}.arm.elf
    printf "${RED}--- Testing ASM ${file} ---${NC}\n"
    if [ ! -f $BASEDIR/test/${category}/${name%.*}.in ]; then
        python3 $BASEDIR/script/functional_checker.py asm $BASEDIR/target/test/${category}/${name}.arm.elf $BASEDIR/test/${category}/${name%.*}.out
    else
        python3 $BASEDIR/script/functional_checker.py asm $BASEDIR/target/test/${category}/${name}.arm.elf $BASEDIR/test/${category}/${name%.*}.out $BASEDIR/test/${category}/${name%.*}.in
    fi
}