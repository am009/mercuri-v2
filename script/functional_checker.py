#! /usr/bin/python3

import sys

def get_commands(elf, is_asm):
    if is_asm:
        return ["qemu-arm", "-L", "/usr/arm-linux-gnueabihf/", elf]
    else:
        return [elf]


def test(target_elf, out_file, in_file=None, is_asm=False):
    in_str = b""
    if (in_file != None):
        with open(in_file, 'rb') as f:
            in_str = f.read()

    from subprocess import Popen,PIPE,STDOUT

    p = Popen([target_elf], stdout=PIPE,  stdin=PIPE, stderr=PIPE)
    out, err = p.communicate(input=in_str)
    code = p.returncode
    if len(out) > 0 and out[-1] != ord('\n'):
        out = out + b'\n'
    out = out + str(code).encode()

    with open(out_file, 'rb') as f:
        s = f.read()

    print(out)
    print(s)

    RED='\033[0;34m'
    NC='\033[0m' # No Color

    if out.strip() == s.strip():
        print(RED+"=========== IR Pass! ==============" +NC)
        return True
    else:
        print(RED+"Result Mismatch"+NC)
        return False


debug_case = None
# debug_case = '54_hidden_var' # uncomment to debug
if debug_case:
    sys.argv = ['debugir', '/mnt/c/Users/warren/d/2022/compiler-contest/mercuri-v2/script/functional_checker.py', f'/mnt/c/Users/warren/d/2022/compiler-contest/mercuri-v2/target/test/functional/{debug_case}.sy.elf', f'/mnt/c/Users/warren/d/2022/compiler-contest/mercuri-v2/test/functional/{debug_case}.out']
print(sys.argv)

if len(sys.argv) < 4:
    print("Usage: {} <mode> target_elf out_file [in_file]")
    exit(-1)

mode = sys.argv[0]
if not mode.startswith("debug"):
    assert debug_case == None # 防止那边自动测试脚本使用到正在debug的该脚本
else:
    mode = mode.removeprefix("debug")


target_elf = sys.argv[2]
out_file = sys.argv[3]
in_file = None
if len(sys.argv) >= 5:
    in_file = sys.argv[4]
if mode == 'ir':
    test(target_elf, out_file, in_file, is_asm=False)
elif mode == 'asm':
    test(target_elf, out_file, in_file, is_asm=True)
