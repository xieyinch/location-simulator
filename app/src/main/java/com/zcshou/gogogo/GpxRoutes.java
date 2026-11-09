package com.zcshou.gogogo;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** GPX 1.1 route/track exchange, with WGS-84 coordinates and bounded input. */
final class GpxRoutes {
    private static final int MAX_BYTES=2_000_000, MAX_POINTS=5000;
    private GpxRoutes() {}
    static ArrayList<double[]> read(InputStream input) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count,total=0;
        while((count=input.read(buffer))!=-1){total+=count;if(total>MAX_BYTES)throw new IllegalArgumentException("文件超过 2 MB");bytes.write(buffer,0,count);}
        XmlPullParserFactory factory=XmlPullParserFactory.newInstance();factory.setNamespaceAware(true);XmlPullParser parser=factory.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);parser.setInput(new ByteArrayInputStream(bytes.toByteArray()),null);
        ArrayList<double[]> points=new ArrayList<>();boolean root=false,track=false,route=false;int segments=0,tracks=0,routes=0;
        for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT;event=parser.nextToken()) {
            if(event==XmlPullParser.DOCDECL)throw new IllegalArgumentException("不支持包含 DTD 的 GPX 文件");
            if(event!=XmlPullParser.START_TAG)continue;
            String name=parser.getName();
            if(!root){if(!"gpx".equals(name))throw new IllegalArgumentException("不是 GPX 文件");root=true;}
            if("trk".equals(name)&&++tracks>1)throw new IllegalArgumentException("请导入仅包含一条轨迹的 GPX");
            if("rte".equals(name)&&++routes>1)throw new IllegalArgumentException("请导入仅包含一条路线的 GPX");
            if("trkseg".equals(name)&&++segments>1)throw new IllegalArgumentException("请导入仅包含一个轨迹段的 GPX");
            if(!"trkpt".equals(name)&&!"rtept".equals(name))continue;
            track|="trkpt".equals(name);route|="rtept".equals(name);if(track&&route)throw new IllegalArgumentException("请导入单条轨迹或路线");
            double lat=Double.parseDouble(parser.getAttributeValue(null,"lat")),lon=Double.parseDouble(parser.getAttributeValue(null,"lon"));
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException("坐标无效");
            if(points.size()>=MAX_POINTS)throw new IllegalArgumentException("路线点超过 5000 个");points.add(new double[]{lat,lon});
        }
        if(!root||points.size()<2)throw new IllegalArgumentException("路线至少需要两个点");return points;
    }
    static String write(List<double[]> points) {
        if(points.size()<2||points.size()>MAX_POINTS)throw new IllegalArgumentException("路线点数量无效");
        StringBuilder out=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"Location Simulator\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>");
        for(double[] p:points){if(p.length<2||!Double.isFinite(p[0])||!Double.isFinite(p[1])||Math.abs(p[0])>90||Math.abs(p[1])>180)throw new IllegalArgumentException("坐标无效");out.append(String.format(Locale.US,"<trkpt lat=\"%.8f\" lon=\"%.8f\"/>",p[0],p[1]));}
        return out.append("</trkseg></trk></gpx>").toString();
    }
}
