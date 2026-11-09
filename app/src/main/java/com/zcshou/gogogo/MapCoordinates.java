package com.zcshou.gogogo;

import com.zcshou.utils.MapUtils;

/** Application coordinates are WGS-84; AMap displays GCJ-02. */
final class MapCoordinates {
    private MapCoordinates() {}
    static boolean outsideChina(double lat,double lon){return lon<72.004||lon>137.8347||lat<0.8293||lat>55.8271;}
    static double[] toGcj(double lat,double lon){
        if(outsideChina(lat,lon)) return new double[]{lat,lon};
        double[] bd=MapUtils.wgs2bd09(lon,lat),gcj=MapUtils.bd09togcj02(bd[0],bd[1]);
        return new double[]{gcj[1],gcj[0]};
    }
    static double[] toWgs(double lat,double lon){
        if(outsideChina(lat,lon)) return new double[]{lat,lon};
        double[] wgs=MapUtils.gcj02towgs84(lon,lat);return new double[]{wgs[1],wgs[0]};
    }
}
