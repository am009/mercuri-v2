package backend;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FlowViewer {
    AsmModule m;

    public FlowViewer(AsmModule m) {
        this.m = m;
    }

    public String viz() {
        var sb = new StringBuilder();
        var arcs = new StringBuilder();
        sb.append("digraph myprog {");
        sb.append("""
                                graph [
                                    label = "Flow"
                                    labelloc = t
                                    fontname = "Helvetica,Arial,sans-serif"
                                    fontsize = 20
                                    layout = dot
                                    rankdir = LR
                                    newrank = true
                                ]
                                node [
                                    style=filled
                                    shape=rect
                                    pencolor="#00000044" // frames color
                                    fontname="Helvetica,Arial,sans-serif"
                                    shape=plaintext
                                ]
                                edge [
                                    arrowsize=0.5
                                    fontname="Helvetica,Arial,sans-serif"
                                    labeldistance=3
                                    labelfontcolor="#00000080"
                                    penwidth=2
                                    style=solid
                                ]

                """);
        for (var func : m.funcs) {
            for (var block : func) {
                sb.append(escape(block.label));
                sb.append("""
                           [
                        color="#88000022"
                        label=<<table border="0" cellborder="1" cellspacing="0" cellpadding="4">
                        """ + renderInsts(block) + """
                        		</table>>
                        		shape=plain
                        	]
                        """);
                for (var succ : block.succ) {
                    arcs.append(escape(block.label) + " -> " + escape(succ.label) + ";\n");
                }
            }
        }
        sb.append(arcs.toString());
        sb.append("                }");
        return sb.toString();
    }

    String escape(String in) {
        return in.replace("\"", "\\\"").replace(".", "");
    }

    String renderInsts(AsmBlock block) {
        var sb = new StringBuilder();
        sb.append("<tr>\n");
        sb.append("<td>");
        sb.append("<b>");
        sb.append(block.label);
        sb.append("</b>\n");
        sb.append("</td>\n");
        sb.append("</tr>\n");
        sb.append("<tr>\n");
        sb.append("<td>");
        for (var inst : block.insts) {
            sb.append(inst.toString().replace(" ", "&nbsp;").replace("#", "&#35;").replace("\t", "&nbsp; "));
            sb.append("<br align=\"left\"/>\n");
        }
        sb.append("</td>\n");
        sb.append("</tr>\n");
        return sb.toString();
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        // render html
        sb.append("<html>\n");
        sb.append("<head>\n");
        // utf-8
        sb.append("<meta charset=\"utf-8\">\n");
        // vis.js
        sb.append("<script src='https://cdnjs.cloudflare.com/ajax/libs/viz.js/1.8.0/viz-lite.js'></script>\n");
        // vis.js style
        sb.append("<title>Flow Viewer</title>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<div id=\"visualization\" style=\"width: 100%; height: 100%;\"></div>\n");
        sb.append("""
                <script id=\"rendered-js\">
                    var sample4 = `""");
        sb.append(viz());
        sb.append("""
                        `;
                    var options = {
                        //format: 'png-image-element' // png
                        format: 'svg' // svg
                        //engine: 'dot'
                    };
                    var image = Viz(sample4, options);
                    console.info(image);
                    document.getElementById('visualization').innerHTML = image;
                </script>
                                """);
        sb.append("</body>\n");
        sb.append("</html>\n");

        return sb.toString();
    }

    public static void process(AsmModule asm) {
        var html = new FlowViewer(asm);
        var htmlFile = new File("log/flow.html");
        try {
            Files.write(htmlFile.toPath(), html.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
