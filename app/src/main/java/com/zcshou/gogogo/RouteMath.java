package com.zcshou.gogogo;
import java.util.List;
/** WGS-84 interpolation by distance in metres. */
public final class RouteMath {
    private RouteMath() {}
    static double[] position(List<double[]> points, double distance) {
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
    static double distance(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]), lat2 = Math.toRadians(b[0]);
        double dlat = lat2 - lat1, dlon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dlat / 2) * Math.sin(dlat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dlon / 2) * Math.sin(dlon / 2);
        return 6371008.8 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)));
    }
}
