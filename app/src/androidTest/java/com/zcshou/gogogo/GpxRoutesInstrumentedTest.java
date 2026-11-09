package com.zcshou.gogogo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class GpxRoutesInstrumentedTest {
    @Test public void roundTripAndRejectInvalidSegments() throws Exception {
        String xml=GpxRoutes.write(Arrays.asList(new double[]{34.23,108.94},new double[]{34.24,108.95}));
        ArrayList<double[]> points=GpxRoutes.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2,points.size());assertEquals(34.23,points.get(0)[0],1e-7);assertEquals(108.95,points.get(1)[1],1e-7);
        String split="<gpx><trk><trkseg><trkpt lat='34' lon='108'/></trkseg><trkseg><trkpt lat='35' lon='108'/></trkseg></trk></gpx>";
        try{GpxRoutes.read(new ByteArrayInputStream(split.getBytes(StandardCharsets.UTF_8)));fail("multiple segments must be rejected");}catch(IllegalArgumentException expected){}
        String unsafe="<!DOCTYPE gpx [ <!ENTITY x SYSTEM 'file:///etc/passwd'> ]><gpx><trk><trkseg><trkpt lat='34' lon='108'/><trkpt lat='35' lon='108'/></trkseg></trk></gpx>";
        try{GpxRoutes.read(new ByteArrayInputStream(unsafe.getBytes(StandardCharsets.UTF_8)));fail("DTD must be rejected");}catch(Exception expected){}
    }
}
