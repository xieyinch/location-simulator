package com.zcshou.gogogo;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class RouteLoopTest {
    @Test public void repeatsWithReturnAndEndsAtDestination() {
        double[] start={34,108}, end={34.001,108}; List<double[]> route=Arrays.asList(start,end);
        double length=RouteMath.distance(start,end);
        assertEquals(start[0],RouteMath.loopPosition(route,0,2)[0],1e-8);
        assertEquals(end[0],RouteMath.loopPosition(route,length,2)[0],1e-8);
        assertEquals(start[0],RouteMath.loopPosition(route,2*length,2)[0],1e-8);
        assertEquals(end[0],RouteMath.loopPosition(route,3*length,2)[0],1e-8);
        assertEquals(end[0],RouteMath.loopPosition(route,4*length,2)[0],1e-8);
    }
    @Test public void pingPongReturnsAlongAllOriginalSegments() {
        double[] a={34,108},b={34.001,108},c={34.001,108.001};List<double[]> route=Arrays.asList(a,b,c);
        double first=RouteMath.distance(a,b),second=RouteMath.distance(b,c),total=first+second;
        assertEquals(c[1],RouteMath.pingPongPosition(route,total,1,total)[1],1e-8);
        double[] returnCorner=RouteMath.pingPongPosition(route,total+second,1,total);assertEquals(b[0],returnCorner[0],1e-7);assertEquals(b[1],returnCorner[1],1e-7);
        assertEquals(a[0],RouteMath.pingPongPosition(route,total*2,1,total)[0],1e-8);
    }
}
