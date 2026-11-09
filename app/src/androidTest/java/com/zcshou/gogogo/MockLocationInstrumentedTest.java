package com.zcshou.gogogo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.zcshou.service.ServiceGo;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Integration test: assert the Android GPS provider actually receives the mock coordinate. */
@RunWith(AndroidJUnit4.class)
public class MockLocationInstrumentedTest {
    @Test public void gpsReceivesSelectedCoordinate() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals(PackageManager.PERMISSION_GRANTED,ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION));
        LocationManager lm=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        LocationListener listener=new LocationListener(){@Override public void onLocationChanged(Location location){} @Override public void onStatusChanged(String p,int s,Bundle e){} @Override public void onProviderEnabled(String p){} @Override public void onProviderDisabled(String p){}};
        Intent intent=new Intent(context,ServiceGo.class).putExtra("points","[{\"lat\":34.23,\"lon\":108.94}]").putExtra("speed",1.2);
        try{
            ContextCompat.startForegroundService(context,intent);
            SystemClock.sleep(1500);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,listener,Looper.getMainLooper()));
            long deadline=SystemClock.elapsedRealtime()+15000;Location result=null;
            while(SystemClock.elapsedRealtime()<deadline){Location candidate=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(candidate!=null&&Math.abs(candidate.getLatitude()-34.23)<0.0001&&Math.abs(candidate.getLongitude()-108.94)<0.0001){result=candidate;break;}SystemClock.sleep(250);}
            String status=context.getSharedPreferences(ServiceGo.STATUS_PREFS,Context.MODE_PRIVATE).getString("text","无状态");
            assertNotNull("系统 GPS 未收到目标坐标，服务状态："+status,result);
            assertTrue("系统未将位置标为模拟位置",result.isFromMockProvider());
            Intent change=new Intent(context,ServiceGo.class).putExtra("points","[{\"lat\":34.24,\"lon\":108.95}]").putExtra("speed",1.2);
            long changeStart=SystemClock.elapsedRealtime();ContextCompat.startForegroundService(context,change);result=null;
            while(SystemClock.elapsedRealtime()-changeStart<5000){Location candidate=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(candidate!=null&&Math.abs(candidate.getLatitude()-34.24)<0.0001&&Math.abs(candidate.getLongitude()-108.95)<0.0001){result=candidate;break;}SystemClock.sleep(100);}
            assertNotNull("运行中切换位置后系统 GPS 未在 5 秒内更新",result);
            assertEquals("固定点速度应与影梭原版一致",1.2f,result.getSpeed(),0.001f);
            SystemClock.sleep(1200);
        }finally{context.stopService(new Intent(context,ServiceGo.class));InstrumentationRegistry.getInstrumentation().runOnMainSync(()->lm.removeUpdates(listener));}
    }
}
