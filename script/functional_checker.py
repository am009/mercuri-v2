#! /usr/bin/python3

import sys

RED='\033[0;34m'
NC='\033[0m' # No Color


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

    commands = get_commands(target_elf, is_asm)
    if (is_asm):
        print(' '.join(commands))
    p = Popen(commands, stdout=PIPE,  stdin=PIPE, stderr=PIPE)
    out, err = p.communicate(input=in_str)
    code = p.returncode
    if len(out) > 0 and out[-1] != ord('\n'):
        out = out + b'\n'
    out = out + str(code).encode()

    with open(out_file, 'rb') as f:
        s = f.read()

    print(out)
    print(s)

    print(err, file=sys.stderr) # perfomance test打印所花时间
    if out.strip() == s.strip():
        print(RED+"=========== Pass! ==============" +NC)
        return True
    else:
        print(RED+"Result Mismatch"+NC)
        return False
import os

proj_dir = os.path.dirname(os.path.dirname(__file__))
debug_case = None
# debug_case = '00_bitset1' # uncomment to debug
if debug_case:
    assert len(sys.argv) == 1
    sys.argv = [f'{proj_dir}/script/functional_checker.py', 'debugasm', f'{proj_dir}/target/test/performance/{debug_case}.sy.arm.elf', f'{proj_dir}/test/performance/{debug_case}.out']
print(' '.join(sys.argv))

if len(sys.argv) < 4:
    print("Usage: {} [ir|debugir|asm|debugasm] target_elf out_file [in_file]")
    exit(-1)

mode = sys.argv[1]
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
    ret = test(target_elf, out_file, in_file, is_asm=False)
    if (not ret):
        exit(-1)
elif mode == 'asm':
    ret = test(target_elf, out_file, in_file, is_asm=True)
    if (not ret):
        exit(-1)
else:
    assert False