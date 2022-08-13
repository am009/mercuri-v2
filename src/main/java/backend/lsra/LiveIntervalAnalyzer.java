package backend.lsra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import backend.AsmBlock;
import backend.AsmInst;
import backend.AsmModule;
import backend.AsmOperand;
import backend.VirtReg;
import backend.arm.inst.ConstrainRegInst;
import ds.Global;

public class LiveIntervalAnalyzer {

    Map<AsmBlock, LiveInfo> liveInfo = new HashMap<AsmBlock, LiveInfo>();
    DagLinearFlow result;
    AsmModule m;

    public LiveIntervalAnalyzer(AsmModule m) {
        this.m = m;
    }

    public static DagLinearFlow process(AsmModule m) {
        var g = new LiveIntervalAnalyzer(m);
        g.doAnalysis();
        return g.result;
    }

    private void doAnalysis() {
        // Step 1: LiveUse, LiveDef
        for (var func : m.funcs) {
            for (var block : func.bbs) {
                analyzeUseDef(block);
            }
        }
        // Step 2: LiveIn, LiveOut
        for (var func : m.funcs) {
            var stable = false;
            var counter = 0;
            var reversedBlocks = new LinkedList<AsmBlock>();
            for (var block : func.bbs) {
                reversedBlocks.add(0, block);
            }
            while (!stable) {
                stable = true;
                for (var block : reversedBlocks) {
                    var blockLiveOutNoChange = analyzeInOut(block);
                    if (!blockLiveOutNoChange) {
                        stable = false;
                    }
                }
                counter++;
                if (counter > 100000) {
                    Global.logger.warning("LiveIntervalAnalyzer: too many iterations");
                }
            }
        }
        // Step 3: LiveInterval
        analyzeLiveInterval();
    }

    LiveInfo liveInfoOf(AsmBlock block) {
        if (!liveInfo.containsKey(block)) {
            liveInfo.put(block, new LiveInfo());
        }
        return liveInfo.get(block);
    }

    // 对给定的 BB 进行 liveUse 和 liveOut 分析
    void analyzeUseDef(AsmBlock block) {
        LiveInfo liveInfo = liveInfoOf(block);
        for (AsmInst inst : block.insts) {
            for (AsmOperand operand : inst.uses) {
                if (operand instanceof VirtReg) {
                    var vr = (VirtReg) operand;
                    if (!liveInfo.liveDef.contains(vr)) {
                        liveInfo.liveUse.add(vr);
                    }
                } else {
                    Global.logger.warning("operand is not VirtReg, but " + operand.getClass().getSimpleName() +" " + operand);
                }
            }
            for (AsmOperand operand : inst.defs) {
                if (operand instanceof VirtReg) {
                    liveInfo.liveDef.add((VirtReg) operand);
                } else {
                    Global.logger.warning("operand is not VirtReg, but " + operand.getClass().getSimpleName() +" " + operand);

                }
            }
        }
    }

    // 对给定的 BB 进行 LiveIn/LiveOut 分析。如果 LiveOut 不产生变化，即稳定，返回 true
    Boolean analyzeInOut(AsmBlock block) {
        var blive = liveInfoOf(block);
        var liveOutBefore = new HashSet<VirtReg>(blive.liveOut);
        for (var succbb : block.succ) {
            blive.liveOut.addAll(liveInfoOf(succbb).liveIn);
        }
        // b.live_gen 是否包括其自己定义的？
        // 答案是不包括，参见 JavaHotSpot LSRA 62 页
        // b.live_in = (b.live_out – b.live_kill) ∪ b.live_gen
        blive.liveIn.clear();
        blive.liveIn.addAll(blive.liveOut);
        blive.liveIn.removeAll(blive.liveDef);
        blive.liveIn.addAll(blive.liveUse);
        var stable = liveOutBefore.equals(blive.liveOut);
        Global.logger.trace("block " + block.label + " liveIn: " + blive.liveIn + " liveOut: " + blive.liveOut
                + " stable: " + stable);
        return stable;
    }

    // 分析活跃区间
    void analyzeLiveInterval() {
        var linearFlow = DagLinearFlow.build(m, liveInfo);
        result = linearFlow;
        debug();
    }

    void debug() {
        var outfile = "log/liveinterval.html";
        var sb = new StringBuilder();
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("<meta charset=\"utf-8\">\n");
        sb.append("<title>LiveInterval</title>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<h1>LiveInterval</h1>\n");
        sb.append("<table border=\"1\" style=\"border-collapse: collapse; width: max-content;\">\n");
        sb.append("<tr>\n");
        sb.append("<th>Block</th>\n");
        sb.append("<th>LiveIn</th>\n");
        sb.append("<th>LiveOut</th>\n");
        sb.append("<th>LiveUse</th>\n");
        sb.append("<th>LiveDef</th>\n");
        sb.append("</tr>\n");
        for (var func : m.funcs) {
            for (var block : func.bbs) {
                var liveInfo = this.liveInfoOf(block);
                sb.append("<tr>\n");
                sb.append("<td>");
                sb.append(block.label);
                sb.append("</td>\n");
                sb.append("<td>");
                sb.append(debugVregs(liveInfo.liveIn));
                sb.append("</td>\n");
                sb.append("<td>");
                sb.append(debugVregs(liveInfo.liveOut));
                sb.append("</td>\n");
                sb.append("<td>");
                sb.append(debugVregs(liveInfo.liveUse));
                sb.append("</td>\n");
                sb.append("<td>");
                sb.append(debugVregs(liveInfo.liveDef));
                sb.append("</td>\n");
                sb.append("</tr>\n");
            }
        }
        sb.append("</table>\n");

        sb.append("<h1>LinearFlow</h1>\n");
        sb.append("<table border=\"1\" style=\"border-collapse: collapse;width: max-content\">\n");
        sb.append("<tr>\n");
        sb.append("<th>Owner Block</th>\n");
        sb.append("<th>Inst</th>\n");
        sb.append("<th>Uses</th>\n");
        sb.append("<th>Defs</th>\n");
        sb.append("<th>Slot</th>\n");

        var ents = result.liveIntervals.entrySet();
        for (var lv : ents) {
            var k = lv.getKey();
            sb.append("<th>" + k.toString().replace("vreg","vr") + (k.isFloat ? "f" : "i") + "</th>");
        }

        sb.append("</tr>\n");

        for (var func : m.funcs) {
            for (var block : func.bbs) {
                var isFirstInst = true;
                for (var inst : block.insts) {
                    var instSlotIndex = result.instSlotIdx.get(inst);
                    sb.append("<tr>\n");
                    if (isFirstInst) {
                        // colspan
                        sb.append("<td rowspan=\"" + block.insts.size() + "\">");
                        sb.append(block.label);
                        sb.append("</td>\n");
                    }
                    sb.append("<td>");
                    sb.append(inst.toString());
                    sb.append("</td>\n");
                    sb.append("<td>");
                    sb.append(debugVregsAsmOperand(inst, inst.uses));
                    sb.append("</td>\n");
                    sb.append("<td>");
                    sb.append(debugVregsAsmOperand(inst, inst.defs));
                    sb.append("</td>\n");
                    sb.append("<td>");
                    sb.append(result.instSlotIdx.get(inst));
                    sb.append("</td>\n");
                    for (var lv : ents) {
                        var liveRange = lv.getValue();
                        var seg = liveRange.getLiveSubRangeAt(instSlotIndex);
                        if (seg == null) {
                            sb.append("<td>");
                        } else {
                            sb.append("<td style=\"background: #c1c1c1; ");
                            // if is end of seg
                            if (seg.end == instSlotIndex) {
                                sb.append("border-bottom: 2px solid #1313ff; ");
                            }
                            if (seg.start == instSlotIndex) {
                                sb.append("border-top: 2px solid #1313ff; ");
                            }
                            // sb.append("border-left: 2px solid #1313ff; ");
                            // sb.append("border-right: 2px solid #1313ff; ");
                            sb.append("\">");
                        }
                        sb.append("</td>\n");
                    }

                    sb.append("</tr>\n");
                    isFirstInst = false;
                }
            }
        }
        for (var lv : ents) {
            var reg = lv.getKey();
            var liveRange = lv.getValue();
            sb.append("<p>LiveRange of ");
            sb.append(reg.toString());
            sb.append("</p>\n");
            sb.append("<p>");
            sb.append(liveRange.toString());
            sb.append("</p>\n");
        }

        sb.append("</body>\n");
        sb.append("</html>\n");
        try {
            Files.write(Paths.get(outfile), sb.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String debugVregs(Iterable<VirtReg> s) {
        StringBuilder sb = new StringBuilder();
        for (var v : s) {
            var isFloat = v.isFloat ? "f" : "i";
            sb.append(v.toString().replace("vreg", "vr") + isFloat + " ");
        }
        return sb.toString();
    }

    public static String debugVregsAsmOperand(AsmInst inst, Iterable<AsmOperand> s) {
        StringBuilder sb = new StringBuilder();
        for (var v : s) {
            var isFloat = v.isFloat ? "f" : "i";
            sb.append(v.toString().replace("vreg", "vr"));
            sb.append(isFloat);

            if (inst instanceof ConstrainRegInst) {
                var constrainRegInst = (ConstrainRegInst) inst;
                constrainRegInst.getConstraints().forEach(action -> {
                    if (action.getKey().equals(v)) {
                        sb.append("(" + action.getValue() + ")");
                    }
                });
            }

            sb.append(" ");

        }
        return sb.toString();
    }
}
