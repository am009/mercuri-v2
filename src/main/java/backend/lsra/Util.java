package backend.lsra;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;

import backend.AsmBlock;
import backend.AsmFunc;

public class Util {
    public static LinkedList<AsmBlock> computeReversePostOrderBlockList(AsmFunc func) {

        var visited = new HashMap<AsmBlock, Boolean>(func.bbs.size());

        var ret = new LinkedList<AsmBlock>();
        var stack = new Stack<AsmBlock>();
        var entry = func.bbs.get(0);
        for (var block : func.bbs) {
            visited.put(block, false);
        }
        stack.push(entry);
        var curr = entry;

        while (!stack.empty()) {
            curr = stack.pop();
            ret.add(curr);
            for (var child : curr.succ) {
                if (!visited.get(child)) {
                    visited.put(child, true);
                    stack.push(child);
                }
            }
        }

        return ret;
    }
}
