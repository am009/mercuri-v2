BASEDIR=$(realpath $(dirname "$0")/..)
echo "BASEDIR: $BASEDIR"
RED='\033[0;34m'
NC='\033[0m' # No Color

printf "${RED}--- Start running...${NC}\n"
RUN_DIR=$BASEDIR/target
java -DlogLevel=trace \

    -jar $RUN_DIR/compiler.jar \
        -S -o /root/run/testcase.s \
        test/spec/000-empty.c