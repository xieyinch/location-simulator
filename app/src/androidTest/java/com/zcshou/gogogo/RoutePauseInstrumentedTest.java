package com.zcshou.gogogo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.zcshou.service.ServiceGo;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class RoutePauseInstrumentedTest {
    @Test public void pauseHoldsAndResumeContinues() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();LocationManager lm=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);SharedPreferences prefs=context.getSharedPreferences(ServiceGo.STATUS_PREFS,Context.MODE_PRIVATE);
        try {
            Intent start=new Intent(context,ServiceGo.class).putExtra("points","[{\"lat\":34.23,\"lon\":108.94},{\"lat\":34.24,\"lon\":108.94}]").putExtra("speed",50.0).putExtra("loops",1).putExtra("ping_pong",true);
            ContextCompat.startForegroundService(context,start);SystemClock.sleep(1500);
            ContextCompat.startForegroundService(context,new Intent(context,ServiceGo.class).setAction(ServiceGo.ACTION_PAUSE));
            long until=SystemClock.elapsedRealtime()+5000;while(!prefs.getBoolean("paused",false)&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);assertTrue("未暂停",prefs.getBoolean("paused",false));
            SystemClock.sleep(300);Location before=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);assertNotNull(before);SystemClock.sleep(1200);Location held=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);assertNotNull(held);assertEquals(before.getLatitude(),held.getLatitude(),0.000003);
            ContextCompat.startForegroundService(context,new Intent(context,ServiceGo.class).setAction(ServiceGo.ACTION_PAUSE));until=SystemClock.elapsedRealtime()+5000;while(prefs.getBoolean("paused",true)&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);assertFalse("未继续",prefs.getBoolean("paused",true));SystemClock.sleep(900);Location moved=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);assertNotNull(moved);assertTrue("继续后位置未移动",moved.getLatitude()>held.getLatitude()+0.00005);
        } finally {context.stopService(new Intent(context,ServiceGo.class));}
    }
}
