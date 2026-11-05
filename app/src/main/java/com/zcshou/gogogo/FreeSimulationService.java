package com.zcshou.gogogo;

import android.app.*;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.*;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** Foreground mock location provider. All coordinates are WGS-84. */
public class FreeSimulationService extends Service {
    public static final String ACTION_STOP="com.zcshou.gogogo.STOP_SIMULATION";
    public static final String ACTION_SHOW_CONTROLS="com.zcshou.gogogo.SHOW_CONTROLS";
    private WindowManager windowManager; private LinearLayout controls; private double[] lastPoint;
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
        double[] current=RouteMath.position(points,distance);
        lastPoint=current.clone();
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
        if(ACTION_SHOW_CONTROLS.equals(intent.getAction())){showControls();return START_NOT_STICKY;}
        ArrayList<double[]> parsed=new ArrayList<>();
        try { JSONArray arr=new JSONArray(intent.getStringExtra("points"));
            for(int i=0;i<arr.length();i++){JSONObject p=arr.getJSONObject(i);double lat=p.getDouble("lat"),lon=p.getDouble("lon"); if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException();parsed.add(new double[]{lat,lon});}
            if(parsed.isEmpty()||parsed.size()>10000)throw new IllegalArgumentException();
            if(!gps&&!network){gps=add(LocationManager.GPS_PROVIDER); network=add(LocationManager.NETWORK_PROVIDER);}
            if(!gps&&!network)throw new SecurityException("mock provider unavailable");
            speed=intent.getDoubleExtra("speed",1.4); if(!Double.isFinite(speed)||speed<=0||speed>100)throw new IllegalArgumentException();
            handler.removeCallbacks(tick);points.clear();points.addAll(parsed);started=SystemClock.elapsedRealtime();handler.post(tick);
        }catch(Exception e){android.util.Log.e("FreeSimulation", "Unable to start mock location",e); Toast.makeText(this,"模拟启动失败，请检查开发者选项和定位设置",Toast.LENGTH_LONG).show(); stopSelf();}
        return START_NOT_STICKY;
    }
    private void showControls(){
        if(points.isEmpty()){Toast.makeText(this,"请先开始模拟位置",Toast.LENGTH_SHORT).show();stopSelf();return;}
        if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"请先授予悬浮窗权限",Toast.LENGTH_SHORT).show();return;}
        if(controls!=null)return;
        windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setBackgroundColor(0xddffffff);
        LinearLayout row=new LinearLayout(this);addControl(row,"↑",0.00005,0);addControl(row,"↓",-0.00005,0);addControl(row,"←",0,-0.00005);addControl(row,"→",0,0.00005);controls.addView(row);
        Button close=new Button(this);close.setText("隐藏摇杆");close.setOnClickListener(v->hideControls());controls.addView(close);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);p.gravity=Gravity.BOTTOM|Gravity.START;p.y=120;
        try{windowManager.addView(controls,p);}catch(Exception e){controls=null;Toast.makeText(this,"无法显示悬浮摇杆",Toast.LENGTH_SHORT).show();}
    }
    private void addControl(LinearLayout row,String label,double lat,double lon){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->{if(lastPoint==null)return;double nlat=Math.max(-90,Math.min(90,lastPoint[0]+lat));double nlon=Math.max(-180,Math.min(180,lastPoint[1]+lon));lastPoint=new double[]{nlat,nlon};points.clear();points.add(lastPoint.clone());started=SystemClock.elapsedRealtime();Toast.makeText(this,"已暂停路线并移动位置",Toast.LENGTH_SHORT).show();});row.addView(b);}
    private void hideControls(){if(controls!=null){try{windowManager.removeView(controls);}catch(Exception ignored){}controls=null;}}
    private boolean add(String provider){try {manager.addTestProvider(provider,false,false,false,false,true,true,true,android.location.Criteria.POWER_LOW,android.location.Criteria.ACCURACY_FINE);manager.setTestProviderEnabled(provider,true);return true;}catch(Exception e){android.util.Log.w("FreeSimulation","Provider unavailable: "+provider,e);return false;}}
    private void publish(String provider,double[] p){Location l=new Location(provider);l.setLatitude(p[0]);l.setLongitude(p[1]);l.setAccuracy(5);l.setAltitude(55);l.setTime(System.currentTimeMillis());l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());manager.setTestProviderLocation(provider,l);}
    @Override public void onDestroy(){handler.removeCallbacks(tick);hideControls();if(gps)try{manager.removeTestProvider(LocationManager.GPS_PROVIDER);}catch(Exception ignored){} if(network)try{manager.removeTestProvider(LocationManager.NETWORK_PROVIDER);}catch(Exception ignored){} super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
