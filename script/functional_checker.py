#! /usr/bin/python3

import sys

sys.argv = ['/mnt/c/Users/warren/d/2022/compiler-contest/mercuri-v2/script/functional_checker.py', '/mnt/c/Users/warren/d/2022/compiler-contest/mercuri-v2/target/test/functional/21_if_test2.sy.elf', '/mnt/c/Users/warren/d/2022/compiler-contest/mercuri-v2/test/functional/21_if_test2.out']
print(sys.argv)

if len(sys.argv) < 3:
    print("Usage: {} target_elf out_file [in_file]")
    exit(-1)

in_str = b""
if len(sys.argv) >= 4:
    with open(sys.argv[3], 'rb') as f:
        in_str = f.read()

from subprocess import Popen,PIPE,STDOUT

p = Popen([sys.argv[1]], stdout=PIPE,  stdin=PIPE, stderr=PIPE)
out, err = p.communicate(input=in_str)
code = p.returncode
out = out + b'\n' + str(code).encode()

with open(sys.argv[2], 'rb') as f:
    s = f.read()

print(out)
print(s)

RED='\033[0;34m'
NC='\033[0m' # No Color

if out.strip() == s.strip():
    print(RED+"=========== Pass! ==============" +NC)
    exit(0)
else:
    print(RED+"Result Mismatch"+NC)
    exit(-1)
