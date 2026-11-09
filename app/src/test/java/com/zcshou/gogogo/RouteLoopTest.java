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
}
