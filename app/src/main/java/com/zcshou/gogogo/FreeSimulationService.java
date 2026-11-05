package com.zcshou.gogogo;

import android.app.*;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.*;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** Foreground mock location provider. All coordinates are WGS-84. */
public class FreeSimulationService extends Service {
    public static final String ACTION_STOP="com.zcshou.gogogo.STOP_SIMULATION";
    private static final String CHANNEL="mock_location";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LocationManager manager;
    private final ArrayList<double[]> points=new ArrayList<>();
    private double speed=1.4;
    private long started;
    private boolean gps,network;
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(points.isEmpty()) return;
        double distance=(SystemClock.elapsedRealtime()-started)/1000.0*speed;
        double[] current=points.get(0);
        for(int i=1;i<points.size();i++){
            double[] next=points.get(i); float[] out=new float[1];
            Location.distanceBetween(current[0],current[1],next[0],next[1],out);
            if(out[0]>0 && distance<out[0]){
                double f=distance/out[0]; current=new double[]{current[0]+(next[0]-current[0])*f,current[1]+(next[1]-current[1])*f}; break;
            }
            distance-=out[0]; current=next;
        }
        try { if(gps) publish(LocationManager.GPS_PROVIDER,current); if(network) publish(LocationManager.NETWORK_PROVIDER,current); }
        catch(SecurityException | IllegalArgumentException e){ stopSelf(); return; }
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(){super.onCreate(); manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"位置模拟",NotificationManager.IMPORTANCE_LOW));
        Intent stop=new Intent(this,FreeSimulationService.class).setAction(ACTION_STOP);
        PendingIntent pi=PendingIntent.getService(this,0,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.mipmap.ic_launcher).setContentTitle("位置模拟运行中").setContentText("点击停止可结束模拟")
          .addAction(0,"停止",pi).setOngoing(true).build();
        startForeground(71,n);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null || ACTION_STOP.equals(intent.getAction())) {stopSelf(); return START_NOT_STICKY;}
        ArrayList<double[]> parsed=new ArrayList<>();
        try { JSONArray arr=new JSONArray(intent.getStringExtra("points"));
            for(int i=0;i<arr.length();i++){JSONObject p=arr.getJSONObject(i);double lat=p.getDouble("lat"),lon=p.getDouble("lon"); if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException();parsed.add(new double[]{lat,lon});}
            if(parsed.isEmpty()||parsed.size()>10000)throw new IllegalArgumentException();
            if(!gps&&!network){gps=add(LocationManager.GPS_PROVIDER); network=add(LocationManager.NETWORK_PROVIDER);}
            if(!gps&&!network)throw new SecurityException("mock provider unavailable");
            speed=intent.getDoubleExtra("speed",1.4); if(!Double.isFinite(speed)||speed<=0||speed>100)throw new IllegalArgumentException();
            handler.removeCallbacks(tick);points.clear();points.addAll(parsed);started=SystemClock.elapsedRealtime();handler.post(tick);
        }catch(Exception e){android.util.Log.e("FreeSimulation", "Unable to start mock location",e); stopSelf();}
        return START_NOT_STICKY;
    }
    private boolean add(String provider){try {manager.addTestProvider(provider,false,false,false,false,true,true,true,android.location.Criteria.POWER_LOW,android.location.Criteria.ACCURACY_FINE);manager.setTestProviderEnabled(provider,true);return true;}catch(Exception e){android.util.Log.w("FreeSimulation","Provider unavailable: "+provider,e);return false;}}
    private void publish(String provider,double[] p){Location l=new Location(provider);l.setLatitude(p[0]);l.setLongitude(p[1]);l.setAccuracy(5);l.setAltitude(55);l.setTime(System.currentTimeMillis());l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());manager.setTestProviderLocation(provider,l);}
    @Override public void onDestroy(){handler.removeCallbacks(tick);for(String p:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})try{manager.removeTestProvider(p);}catch(Exception ignored){} super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
