#
# 此脚本从 g4 文件利用 antlr 生成 lexer 和 parser
#
# 用法：
# ./script/gen.sh
#

BASEDIR=$(realpath $(dirname "$0")/..)
echo "BASEDIR: $BASEDIR"
RED='\033[0;34m'
NC='\033[0m' # No Color

ANTLR_LIB="$BASEDIR/lib/antlr4-4.8-1-complete.jar"
printf "${RED}--- Start generating...${NC}\n"
GRAMMAR_FILE="$BASEDIR/src/main/java/ast/Sysy.g4"
java -jar $ANTLR_LIB -visitor -package ast -o $BASEDIR/src/main/java/ast $GRAMMAR_FILE