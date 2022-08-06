package backend.ra;

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
import ds.Global;

public class LiveIntervalAnalyzer {

    Map<AsmBlock, LiveInfo> liveInfo = new HashMap<AsmBlock, LiveInfo>();
    DagLinearFlow result;
    AsmModule m;

    public LiveIntervalAnalyzer(AsmModule m) {
        this.m = m;
    }

    public static AsmModule process(AsmModule m) {
        var g = new LiveIntervalAnalyzer(m);
        g.doAnalysis();
        return m;
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
                    liveInfo.liveUse.add((VirtReg) operand);
                } else {
                    Global.logger.error("operand is not VirtReg");
                }
            }
            for (AsmOperand operand : inst.defs) {
                if (operand instanceof VirtReg) {
                    liveInfo.liveDef.add((VirtReg) operand);
                } else {
                    Global.logger.error("operand is not VirtReg");
                }
            }
        }
    }

    // 对给定的 BB 进行 LiveIn/LiveOut 分析。如果 LiveOut 不产生变化，即稳定，返回 true
    Boolean analyzeInOut(AsmBlock block) {
        var blive = liveInfoOf(block);
        var liveOutBefore = new HashSet<VirtReg>(blive.liveOut);
        for (var predbb : block.pred) {
            blive.liveOut.addAll(liveInfoOf(predbb).liveIn);
        }
        blive.liveIn.clear();
        blive.liveIn.addAll(blive.liveOut);
        blive.liveIn.removeAll(blive.liveDef);
        blive.liveIn.addAll(blive.liveUse);
        var stable = liveOutBefore.equals(blive.liveOut);
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
        sb.append("<table border=\"1\" style=\"border-collapse: collapse;\">\n");
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
        sb.append("<table border=\"1\" style=\"border-collapse: collapse;\">\n");
        sb.append("<tr>\n");
        sb.append("<th>Owner Block</th>\n");
        sb.append("<th>Inst</th>\n");
        sb.append("<th>Uses</th>\n");
        sb.append("<th>Defs</th>\n");
        sb.append("<th>Slot ID</th>\n");

        var ents = result.liveIntervals.entrySet();
        for (var lv : ents) {
            sb.append("<th>" + lv.getKey() + "</th>");
        }

        sb.append("</tr>\n");

        for (var func : m.funcs) {
            for (var block : func.bbs) {
                var isFirstInst = true;
                for (var inst : block.insts) {
                    var instSlotIndex = result.instSlotIdx.get(inst);
                    sb.append("<tr>\n");
                    if(isFirstInst){
                        // colspan
                        sb.append("<td rowspan=\"" + block.insts.size() + "\">");
                        sb.append(block.label);
                        sb.append("</td>\n");
                    }
                    sb.append("<td>");
                    sb.append(inst.toString());
                    sb.append("</td>\n");
                    sb.append("<td>");
                    sb.append(debugVregsAsmOperand(inst.uses));
                    sb.append("</td>\n");
                    sb.append("<td>");
                    sb.append(debugVregsAsmOperand(inst.defs));
                    sb.append("</td>\n");
                    sb.append("<td>");
                    sb.append(result.instSlotIdx.get(inst));
                    sb.append("</td>\n");
                    for (var lv : ents) {
                        var liveRange = lv.getValue();
                        var seg = liveRange.getLiveSegmentAt(instSlotIndex);
                        if (seg == null) {
                            sb.append("<td>");
                        } else {
                            sb.append("<td style=\"background: #131517; ");
                            // if is end of seg
                            if (seg.end == instSlotIndex) {
                                sb.append("border-bottom: 4px solid #13f5ff; ");
                            }
                            if(seg.start == instSlotIndex){
                                sb.append("border-top: 4px solid #13f5ff; ");
                            }
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
            sb.append(v.toString() + isFloat + " ");
        }
        return sb.toString();
    }

    public static String debugVregsAsmOperand(Iterable<AsmOperand> s) {
        StringBuilder sb = new StringBuilder();
        for (var v : s) {
            var isFloat = v.isFloat ? "f" : "i";
            sb.append(v.toString() + isFloat + " ");
        }
        return sb.toString();
    }
}
