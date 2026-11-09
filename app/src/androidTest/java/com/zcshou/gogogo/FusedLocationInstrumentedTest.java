package com.zcshou.gogogo;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.zcshou.service.ServiceGo;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FusedLocationInstrumentedTest {
    @Test public void reportsFusedAndPreservesGps() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocationManager lm=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
        try {
            ContextCompat.startForegroundService(c,new Intent(c,ServiceGo.class).putExtra("points","[{\"lat\":34.23,\"lon\":108.94}]").putExtra("speed",1.2));
            long deadline=SystemClock.elapsedRealtime()+15000;
            String status="";
            while(SystemClock.elapsedRealtime()<deadline) {
                status=c.getSharedPreferences(ServiceGo.STATUS_PREFS,Context.MODE_PRIVATE).getString("text","");
                if(status.contains("GPS 已提交")&&status.contains("融合 "))break;
                SystemClock.sleep(200);
            }
            assertTrue("GPS submission missing: "+status,status.contains("GPS 已提交"));
            assertTrue("Fused diagnostic missing: "+status,status.contains("融合 "));
            if(Build.VERSION.SDK_INT>=31&&status.contains("融合 已提交")){
                Location result=lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER);
                assertNotNull("Fused channel reported success but returned no position",result);
                assertEquals(34.23,result.getLatitude(),0.0001);
                assertEquals(108.94,result.getLongitude(),0.0001);
                assertTrue(result.isFromMockProvider());
            }
        } finally { c.stopService(new Intent(c,ServiceGo.class)); }
    }
}
