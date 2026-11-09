package com.zcshou.gogogo;
import java.util.List;
/** WGS-84 interpolation by distance in metres. */
public final class RouteMath {
    private RouteMath() {}
    public static double[] position(List<double[]> points, double distance) {
        if (points.isEmpty()) throw new IllegalArgumentException("empty route");
        double[] current = points.get(0);
        distance = Math.max(0, distance);
        for (int i = 1; i < points.size(); i++) {
            double[] next = points.get(i);
            double metres = distance(current, next);
            if (metres > 0 && distance < metres) {
                double f = distance / metres;
                double delta = ((next[1] - current[1] + 540) % 360) - 180;
                double lon = ((current[1] + f * delta + 540) % 360) - 180;
                return new double[]{current[0] + f * (next[0] - current[0]), lon};
            }
            distance -= metres;
            current = next;
        }
        return current.clone();
    }
    /** Repeat the outward route, returning in a straight line between passes; end at destination. */
    public static double totalDistance(List<double[]> points,double outward,int loops) {
        if(points.size()<2 || outward<=0) return 0;
        return outward * loops + distance(points.get(points.size()-1),points.get(0)) * (loops-1);
    }
    public static double[] loopPosition(List<double[]> points,double travelled,int loops) {
        if(points.size()<2)return position(points,0);
        double outward=0;for(int i=1;i<points.size();i++)outward+=distance(points.get(i-1),points.get(i));
        return loopPosition(points,travelled,loops,outward);
    }
    public static double[] loopPosition(List<double[]> points,double travelled,int loops,double outward) {
        if(points.size()<2)return position(points,0);
        if(outward<=0)return points.get(points.size()-1).clone();
        double back=distance(points.get(points.size()-1),points.get(0));
        double cycle=outward+back, total=totalDistance(points,outward,loops);
        if(travelled>=total)return points.get(points.size()-1).clone();
        double within=Math.max(0,travelled)%cycle;
        if(within<=outward)return position(points,within);
        return position(java.util.Arrays.asList(points.get(points.size()-1),points.get(0)),within-outward);
    }
    public static double distance(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]), lat2 = Math.toRadians(b[0]);
        double dlat = lat2 - lat1, dlon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dlat / 2) * Math.sin(dlat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dlon / 2) * Math.sin(dlon / 2);
        return 6371008.8 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)));
    }
}
