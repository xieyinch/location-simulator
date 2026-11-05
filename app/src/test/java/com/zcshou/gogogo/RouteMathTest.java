package com.zcshou.gogogo;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
public class RouteMathTest {
    @Test public void startsInterpolatesAndStopsAtEnd() {
        double[] a={34,108}, b={34,108.01}, c={34.01,108.01};
        List<double[]> p=Arrays.asList(a,b,c);
        assertArrayEquals(a,RouteMath.position(p,0),1e-8);
        assertArrayEquals(new double[]{34,108.005},RouteMath.position(p,RouteMath.distance(a,b)/2),1e-6);
        assertArrayEquals(c,RouteMath.position(p,100000),1e-8);
    }
    @Test public void skipsRepeatedPoints() {
        double[] a={0,0}, b={0,0.01};
        assertArrayEquals(new double[]{0,0.005},RouteMath.position(Arrays.asList(a,a,b),RouteMath.distance(a,b)/2),1e-6);
    }
    @Test public void crossesDateLineOnShortArc() {
        double[] a={0,179.9}, b={0,-179.9};
        assertEquals(180,Math.abs(RouteMath.position(Arrays.asList(a,b),RouteMath.distance(a,b)/2)[1]),1e-6);
    }
}
