package com.cardpricer.service;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Portable glyph outlines with Unicode ActualText. Missing system glyphs fail explicitly. */
final class UnicodePdfText {
    private UnicodePdfText() {}
    static String page(List<String> lines,float size,float x,float top,float leading) throws IOException {
        Font font=new Font(Font.MONOSPACED,Font.PLAIN,1).deriveFont(size);
        FontRenderContext context=new FontRenderContext(null,true,true);
        StringBuilder out=new StringBuilder();
        int lineNumber=0;
        for (String line:lines) {
            if (font.canDisplayUpTo(line)!=-1) throw new IOException("A receipt character is unavailable in installed fonts. Install a font for that language; the UTF-8 receipt is preserved.");
            String hex="FEFF"+HexFormat.of().formatHex((line+"\n").getBytes(StandardCharsets.UTF_16BE));
            out.append("/Span << /ActualText <").append(hex).append("> >> BDC\nq\n1 0 0 -1 ")
                    .append(number(x)).append(' ').append(number(top-lineNumber++*leading)).append(" cm\n");
            if (!line.isEmpty()) {
                PathIterator path=new TextLayout(line,font,context).getOutline(null).getPathIterator(null);
                double[] values=new double[6];double lastX=0,lastY=0;
                while (!path.isDone()) {
                    int type=path.currentSegment(values);
                    switch(type) {
                        case PathIterator.SEG_MOVETO -> { out.append(number(values[0])).append(' ').append(number(values[1])).append(" m\n"); lastX=values[0];lastY=values[1]; }
                        case PathIterator.SEG_LINETO -> { out.append(number(values[0])).append(' ').append(number(values[1])).append(" l\n"); lastX=values[0];lastY=values[1]; }
                        case PathIterator.SEG_QUADTO -> {
                            out.append(number(lastX+2*(values[0]-lastX)/3)).append(' ').append(number(lastY+2*(values[1]-lastY)/3)).append(' ')
                                    .append(number(values[2]+2*(values[0]-values[2])/3)).append(' ').append(number(values[3]+2*(values[1]-values[3])/3)).append(' ')
                                    .append(number(values[2])).append(' ').append(number(values[3])).append(" c\n");lastX=values[2];lastY=values[3];
                        }
                        case PathIterator.SEG_CUBICTO -> {
                            for (double value:values) out.append(number(value)).append(' ');
                            out.append("c\n");lastX=values[4];lastY=values[5];
                        }
                        case PathIterator.SEG_CLOSE -> out.append("h\n");
                    }
                    path.next();
                }
                out.append("f\n");
            }
            out.append("Q\nEMC\n");
        }
        return out.toString();
    }
    private static String number(double number) { return String.format(Locale.ROOT,"%.4f",number); }
}
