#
# 此脚本用于构建和打包 compiler.jar
#
# 用法：
# ./script/build.sh 
#
BASEDIR=$(realpath $(dirname "$0")/..)
echo "BASEDIR: $BASEDIR"
ANTLR_LIB=$BASEDIR/lib/antlr4-4.8-1-complete.jar
echo "ANTLR_LIB: $ANTLR_LIB"
# 编译生成位置
RUN_DIR=$BASEDIR/target
echo "RUN_DIR: $RUN_DIR"
# 源码文件路径
SRC_FILES=$(find -type f -name "*.java" -printf "%p ")
echo "SRC_FILES: $SRC_FILES"
# 编译
echo "Compiling..."
javac -d $RUN_DIR/classes \
      -encoding utf-8 \
      -cp .:$ANTLR_LIB \
      -sourcepath $BASEDIR $SRC_FILES \
      -Xlint

if [ $? -ne 0 ]; then
    echo "Compile failed"
    exit 1
fi

cd $RUN_DIR/classes
echo "Packing jar..."
jar xf $ANTLR_LIB
jar --create --file $RUN_DIR/compiler.jar --main-class Compiler -C $RUN_DIR/classes .