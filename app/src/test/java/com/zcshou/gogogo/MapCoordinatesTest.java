package com.zcshou.gogogo;
import org.junit.Test;
import static org.junit.Assert.*;
public class MapCoordinatesTest {
 @Test public void xianRoundTrip(){double lat=34.23,lon=108.94;double[] g=MapCoordinates.toGcj(lat,lon),w=MapCoordinates.toWgs(g[0],g[1]);assertEquals(lat,w[0],0.00005);assertEquals(lon,w[1],0.00005);assertTrue(Math.abs(g[1]-lon)>0.001);}
 @Test public void outsideChinaUnchanged(){assertArrayEquals(new double[]{51.5,-0.12},MapCoordinates.toGcj(51.5,-0.12),1e-9);assertArrayEquals(new double[]{51.5,-0.12},MapCoordinates.toWgs(51.5,-0.12),1e-9);}
}
