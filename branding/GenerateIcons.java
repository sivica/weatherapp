import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Renders the owned SVG geometry; never processes the comparison-board bitmap. */
public final class GenerateIcons {
    record Layer(Shape shape, Color color) {}
    static final String XML="<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
    static final String VECTOR="<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"108dp\" android:height=\"108dp\" android:viewportWidth=\"108\" android:viewportHeight=\"108\">\n";
    static Path2D path(String input) {
        var values=new ArrayList<String>();var matcher=Pattern.compile("[MCLZ]|-?[0-9]+(?:\\.[0-9]+)?").matcher(input);
        while(matcher.find())values.add(matcher.group());
        var result=new Path2D.Double();int index=0;
        while(index<values.size()) {
            String command=values.get(index++);
            switch(command) {
                case "M" -> {result.moveTo(Double.parseDouble(values.get(index++)),Double.parseDouble(values.get(index++)));}
                case "L" -> {result.lineTo(Double.parseDouble(values.get(index++)),Double.parseDouble(values.get(index++)));}
                case "C" -> {result.curveTo(Double.parseDouble(values.get(index++)),Double.parseDouble(values.get(index++)),Double.parseDouble(values.get(index++)),Double.parseDouble(values.get(index++)),Double.parseDouble(values.get(index++)),Double.parseDouble(values.get(index++)));}
                case "Z" -> result.closePath();
                default -> throw new IllegalArgumentException("Unsupported owned SVG path command");
            }
        }
        return result;
    }
    static String data(Shape shape) {
        var result=new StringBuilder();var iterator=shape.getPathIterator(null);double[] coordinates=new double[6];
        while(!iterator.isDone()) {
            int type=iterator.currentSegment(coordinates);int count=switch(type){case 0,1->2;case 2->4;case 3->6;default->0;};
            result.append(switch(type){case 0->"M";case 1->"L";case 2->"Q";case 3->"C";default->"Z";});
            for(int i=0;i<count;i++)result.append(String.format(Locale.ROOT,"%.4f",coordinates[i])).append(i+1==count?' ':',');
            iterator.next();
        }
        return result.toString().trim();
    }
    static List<Layer> read(Path source)throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var document=factory.newDocumentBuilder().parse(source.toFile());var elements=document.getDocumentElement().getChildNodes();var layers=new ArrayList<Layer>();
        for(int i=0;i<elements.getLength();i++)if(elements.item(i) instanceof Element element) {
            Shape shape=switch(element.getTagName()) {
                case "rect" -> new Rectangle2D.Double(0,0,108,108);
                case "circle" -> {double x=Double.parseDouble(element.getAttribute("cx")),y=Double.parseDouble(element.getAttribute("cy")),r=Double.parseDouble(element.getAttribute("r"));yield new Ellipse2D.Double(x-r,y-r,2*r,2*r);}
                case "path" -> path(element.getAttribute("d"));
                default -> null;
            };
            if(shape!=null)layers.add(new Layer(shape,Color.decode(element.getAttribute("fill"))));
        }
        if(layers.size()!=3)throw new IllegalArgumentException("Expected sky, sun and cloud layers");
        return layers;
    }
    static void render(List<Layer> layers,Path file,int size,String mask,boolean monochrome)throws Exception {
        var image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        Shape clip=switch(mask){case "circle"->new Ellipse2D.Double(0,0,size,size);case "rounded"->new RoundRectangle2D.Double(0,0,size,size,size*.46,size*.46);default->new Rectangle2D.Double(0,0,size,size);};
        graphics.clip(clip);graphics.scale(size/72.0,size/72.0);graphics.translate(-18,-18);
        if(monochrome){graphics.setColor(new Color(0xDDE9FF));graphics.fill(layers.get(0).shape);graphics.setColor(new Color(0x17385E));graphics.fill(mono(layers));}
        else for(var layer:layers){graphics.setColor(layer.color);graphics.fill(layer.shape);}
        graphics.dispose();Files.createDirectories(file.getParent());ImageIO.write(image,"png",file.toFile());
    }
    static Area mono(List<Layer> layers) {
        var sun=new Area(layers.get(1).shape);var cloud=layers.get(2).shape;
        sun.subtract(new Area(new BasicStroke(2.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(cloud)));
        sun.add(new Area(cloud));return sun;
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),preview=Path.of(args[1]).toAbsolutePath();
        var layers=read(root.resolve("branding/vedro-icon.svg"));var res=root.resolve("app/src/main/res");
        for(int index=0;index<5;index++){
            String density=new String[]{"mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"}[index];int size=new int[]{48,72,96,144,192}[index];
            render(layers,res.resolve("mipmap-"+density+"/ic_launcher.png"),size,"rounded",false);
            render(layers,res.resolve("mipmap-"+density+"/ic_launcher_round.png"),size,"circle",false);
        }
        var foreground=new StringBuilder(XML+VECTOR);
        for(var layer:layers.subList(1,3))foreground.append("    <path android:fillColor=\"").append(String.format("#%06X",layer.color.getRGB()&0xffffff)).append("\" android:pathData=\"").append(data(layer.shape)).append("\" />\n");
        foreground.append("</vector>\n");Files.writeString(res.resolve("drawable/ic_launcher_foreground.xml"),foreground);
        Files.writeString(res.resolve("drawable/ic_launcher_background.xml"),XML+VECTOR+"    <path android:fillColor=\"#2FA3FA\" android:pathData=\"M0,0h108v108h-108z\" />\n</vector>\n");
        Files.writeString(res.resolve("drawable/ic_launcher_monochrome.xml"),XML+VECTOR+"    <path android:fillColor=\"#FFFFFFFF\" android:pathData=\""+data(mono(layers))+"\" />\n</vector>\n");
        render(layers,root.resolve("branding/vedro-play-icon.png"),512,"square",false);
        Files.createDirectories(preview);render(layers,preview.resolve("square-512.png"),512,"square",false);render(layers,preview.resolve("adaptive-circle-192.png"),192,"circle",false);render(layers,preview.resolve("themed-circle-192.png"),192,"circle",true);render(layers,preview.resolve("small-48.png"),48,"rounded",false);
        System.out.println("Vedro legacy/adaptive/monochrome/store assets generated from the owned vector geometry.");
    }
}
