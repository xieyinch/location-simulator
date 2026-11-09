package com.zcshou.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import com.zcshou.gogogo.FreeMapActivity;
import com.zcshou.gogogo.MainActivity;
import com.zcshou.gogogo.R;
import com.zcshou.gogogo.RouteMath;
import com.zcshou.joystick.RockerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** GoGoGo 1.12.3 GPS/NETWORK test-provider service, with route and status support. */
public class ServiceGo extends Service {
    public static final String ACTION_STOP="com.zcshou.gogogo.STOP_SIMULATION";
    public static final String ACTION_SHOW_CONTROLS="com.zcshou.gogogo.SHOW_CONTROLS";
    public static final String ACTION_STATUS="com.zcshou.gogogo.SIMULATION_STATUS";
    public static final String STATUS_PREFS="location_status";
    private static final String CHANNEL="SERVICE_GO_NOTE";
    private static final int NOTIFICATION_ID=71;
    private static final long PERIOD_MS=100;
    private final IBinder binder=new ServiceGoBinder();
    private final Object lock=new Object();
    private final ArrayList<double[]> points=new ArrayList<>();
    private LocationManager manager;
    private HandlerThread locationThread;
    private Handler worker;
    private boolean gps,network,running;
    private volatile boolean failed;
    private double latitude,longitude,altitude=55.0,speed=1.2,routeLength;
    private float bearing;
    private double joyAngle,joyStrength,joySpeed=1.2;
    private long routeStarted,lastTick,lastStatus;
    private int failedTicks;
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private LinearLayout controls;
    private TextView speedLabel;

    @Override public void onCreate(){
        super.onCreate();
        manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"位置模拟",NotificationManager.IMPORTANCE_LOW));
        Intent stop=new Intent(this,ServiceGo.class).setAction(ACTION_STOP);
        PendingIntent stopIntent=PendingIntent.getService(this,0,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,FreeMapActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("位置模拟运行中").setContentText("系统定位模拟已启动，可返回应用查看状态")
                .setContentIntent(open).addAction(0,"停止",stopIntent).setOngoing(true).build();
        startForeground(NOTIFICATION_ID,notification);
        locationThread=new HandlerThread("ServiceGoLocation",Process.THREAD_PRIORITY_FOREGROUND);
        locationThread.start();worker=new Handler(locationThread.getLooper());
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(ACTION_SHOW_CONTROLS.equals(intent.getAction())){showControls();return START_NOT_STICKY;}
        try{
            ArrayList<double[]> next=new ArrayList<>();String json=intent.getStringExtra("points");
            if(json!=null){JSONArray a=new JSONArray(json);if(a.length()==0||a.length()>10000)throw new IllegalArgumentException("路线点数量无效");
                for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);double lat=p.getDouble("lat"),lon=p.getDouble("lon");validate(lat,lon);next.add(new double[]{lat,lon});}}
            else if(intent.hasExtra(MainActivity.LAT_MSG_ID)&&intent.hasExtra(MainActivity.LNG_MSG_ID)){
                double lat=intent.getDoubleExtra(MainActivity.LAT_MSG_ID,Double.NaN),lon=intent.getDoubleExtra(MainActivity.LNG_MSG_ID,Double.NaN);validate(lat,lon);next.add(new double[]{lat,lon});
            }else throw new IllegalArgumentException("没有提供位置");
            double nextSpeed=intent.getDoubleExtra("speed",1.2);if(!Double.isFinite(nextSpeed)||nextSpeed<=0||nextSpeed>100)throw new IllegalArgumentException("速度无效");
            updateStatus("正在启动模拟定位…",false);
            worker.post(()->startPosition(next,nextSpeed));
        }catch(Exception e){failed=true;updateStatus("无法启动："+e.getMessage(),false);stopSelf();}
        return START_NOT_STICKY;
    }
    private static void validate(double lat,double lon){if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException("坐标无效");}
    private void startPosition(ArrayList<double[]> next,double nextSpeed){
        if(!gps&&!network){
            // Follow ServiceGo 1.12.3: reset and register NETWORK and GPS independently.
            removeProvider(LocationManager.NETWORK_PROVIDER);network=addProvider(LocationManager.NETWORK_PROVIDER);
            removeProvider(LocationManager.GPS_PROVIDER);gps=addProvider(LocationManager.GPS_PROVIDER);
        }
        if(!gps&&!network){failed=true;updateStatus("启动失败：系统未允许模拟位置。请在开发者选项中选择本应用，并确认系统定位已开启。",false);stopSelf();return;}
        failed=false;
        synchronized(lock){points.clear();points.addAll(next);latitude=points.get(0)[0];longitude=points.get(0)[1];speed=nextSpeed;routeLength=0;for(int i=1;i<points.size();i++)routeLength+=RouteMath.distance(points.get(i-1),points.get(i));joyStrength=0;routeStarted=SystemClock.elapsedRealtime();lastTick=routeStarted;failedTicks=0;running=true;}
        worker.removeCallbacks(tick);worker.post(tick);
        if(Settings.canDrawOverlays(this))new Handler(getMainLooper()).post(this::showControls);
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        final double lat,lon;final float b,velocity;
        synchronized(lock){if(!running)return;long now=SystemClock.elapsedRealtime();double dt=Math.max(0,Math.min(1,(now-lastTick)/1000.0));lastTick=now;
            if(joyStrength>0){double metres=joySpeed*joyStrength*dt;double angle=Math.toRadians(joyAngle);latitude+=metres*Math.sin(angle)/110574.0;double cos=Math.cos(Math.toRadians(latitude));longitude+=metres*Math.cos(angle)/(111320.0*Math.max(0.01,Math.abs(cos)));latitude=Math.max(-90,Math.min(90,latitude));longitude=((longitude+540)%360)-180;bearing=(float)((90-joyAngle+360)%360);velocity=(float)(joySpeed*joyStrength);}
            else{double distance=(now-routeStarted)/1000.0*speed;double[] p=RouteMath.position(points,distance);if(p[0]!=latitude||p[1]!=longitude){Location old=new Location("old");old.setLatitude(latitude);old.setLongitude(longitude);Location next=new Location("next");next.setLatitude(p[0]);next.setLongitude(p[1]);bearing=(old.bearingTo(next)+360)%360;}latitude=p[0];longitude=p[1];velocity=routeLength>0&&distance<routeLength?(float)speed:0f;}
            lat=latitude;lon=longitude;b=bearing;
        }
        boolean gpsOk=gps&&publishGps(lat,lon,b,velocity),networkOk=network&&publishNetwork(lat,lon,b,velocity);
        if(!gpsOk&&!networkOk){failedTicks++;if(failedTicks>=3){failed=true;updateStatus("定位发布失败：GPS 和网络通道均未被系统接收。请重新选择模拟位置应用并检查定位开关。",false);stopSelf();return;}}
        else{failedTicks=0;long now=SystemClock.elapsedRealtime();if(now-lastStatus>=2000){lastStatus=now;updateStatus("系统已接收模拟位置 · GPS "+(gpsOk?"正常":"不可用")+" / 网络 "+(networkOk?"正常":"不可用"),true);}}
        worker.postDelayed(this,PERIOD_MS);
    }};

    @SuppressLint("wrongconstant")
    private boolean addProvider(String provider){
        try{
            boolean isGps=LocationManager.GPS_PROVIDER.equals(provider);
            // Parameters mirror GoGoGo's original GPS and NETWORK provider registration.
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S){
                if(isGps)manager.addTestProvider(provider,false,true,false,false,true,true,true,ProviderProperties.POWER_USAGE_HIGH,ProviderProperties.ACCURACY_FINE);
                else manager.addTestProvider(provider,true,false,true,true,true,true,true,ProviderProperties.POWER_USAGE_LOW,ProviderProperties.ACCURACY_COARSE);
            }else{
                if(isGps)manager.addTestProvider(provider,false,true,false,false,true,true,true,Criteria.POWER_HIGH,Criteria.ACCURACY_FINE);
                else manager.addTestProvider(provider,true,false,true,true,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_COARSE);
            }
            manager.setTestProviderEnabled(provider,true);return true;
        }catch(Exception e){Log.e("ServiceGo","Cannot register "+provider,e);return false;}
    }
    private void removeProvider(String provider){try{manager.removeTestProvider(provider);}catch(Exception ignored){}}
    private boolean publishGps(double lat,double lon,float b,float velocity){
        try{Location location=new Location(LocationManager.GPS_PROVIDER);location.setAccuracy(Criteria.ACCURACY_FINE);location.setAltitude(altitude);location.setBearing(b);location.setLatitude(lat);location.setLongitude(lon);location.setTime(System.currentTimeMillis());location.setSpeed(velocity);location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());Bundle extras=new Bundle();extras.putInt("satellites",7);location.setExtras(extras);manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,location);return true;}catch(Exception e){Log.w("ServiceGo","GPS update failed",e);return false;}
    }
    private boolean publishNetwork(double lat,double lon,float b,float velocity){
        try{Location location=new Location(LocationManager.NETWORK_PROVIDER);location.setAccuracy(Criteria.ACCURACY_COARSE);location.setAltitude(altitude);location.setBearing(b);location.setLatitude(lat);location.setLongitude(lon);location.setTime(System.currentTimeMillis());location.setSpeed(velocity);location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());manager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER,location);return true;}catch(Exception e){Log.w("ServiceGo","NETWORK update failed",e);return false;}
    }
    private void updateStatus(String text,boolean active){getSharedPreferences(STATUS_PREFS,MODE_PRIVATE).edit().putString("text",text).putBoolean("active",active).putLong("time",System.currentTimeMillis()).apply();sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName()).putExtra("text",text));}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private void showControls(){
        synchronized(lock){if(!running){Toast.makeText(this,"请先开始位置模拟",Toast.LENGTH_SHORT).show();stopSelf();return;}}
        if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"请先授予悬浮窗权限",Toast.LENGTH_LONG).show();return;}if(controls!=null)return;
        windowManager=(WindowManager)getSystemService(Context.WINDOW_SERVICE);controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setGravity(Gravity.CENTER);controls.setPadding(dp(6),dp(4),dp(6),dp(6));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xdd26272a);bg.setCornerRadius(dp(18));controls.setBackground(bg);
        TextView handle=new TextView(this);handle.setText("拖动 · 隐藏");handle.setTextColor(Color.WHITE);handle.setTextSize(13);handle.setGravity(Gravity.CENTER);handle.setPadding(dp(12),dp(8),dp(12),dp(8));controls.addView(handle);
        RockerView rocker=new RockerView(this);rocker.setContentDescription("圆形方向摇杆，拖动移动，松开停止；轻点中央可锁定方向");rocker.setListener((auto,angle,strength)->{
            synchronized(lock){if(strength>0.01&&joyStrength<=0.01&&points.size()>1){points.clear();points.add(new double[]{latitude,longitude});routeLength=0;routeStarted=SystemClock.elapsedRealtime();}if(strength<=0.01&&joyStrength>0.01){points.clear();points.add(new double[]{latitude,longitude});routeLength=0;routeStarted=SystemClock.elapsedRealtime();}joyAngle=angle;joyStrength=Math.max(0,Math.min(1,strength));lastTick=SystemClock.elapsedRealtime();}
        });controls.addView(rocker,new LinearLayout.LayoutParams(dp(124),dp(124)));
        LinearLayout speedRow=new LinearLayout(this);speedRow.setGravity(Gravity.CENTER);speedLabel=new TextView(this);speedLabel.setTextColor(Color.WHITE);speedLabel.setTextSize(12);speedLabel.setText("步行");speedLabel.setGravity(Gravity.CENTER);controls.addView(speedLabel);addSpeed(speedRow,"步",1.2,"步行");addSpeed(speedRow,"跑",3.0,"跑步");addSpeed(speedRow,"骑",5.0,"骑行");controls.addView(speedRow);
        windowParams=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);windowParams.gravity=Gravity.TOP|Gravity.START;windowParams.x=dp(16);windowParams.y=dp(160);
        handle.setOnTouchListener(new android.view.View.OnTouchListener(){float x,y;int wx,wy;boolean dragged;public boolean onTouch(android.view.View v,MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:x=e.getRawX();y=e.getRawY();wx=windowParams.x;wy=windowParams.y;dragged=false;return true;case MotionEvent.ACTION_MOVE:int dx=(int)(e.getRawX()-x),dy=(int)(e.getRawY()-y);if(Math.abs(dx)+Math.abs(dy)>dp(6))dragged=true;windowParams.x=wx+dx;windowParams.y=wy+dy;try{windowManager.updateViewLayout(controls,windowParams);}catch(Exception ignored){}return true;case MotionEvent.ACTION_UP:if(!dragged)hideControls();return true;default:return true;}}});
        try{windowManager.addView(controls,windowParams);}catch(Exception e){controls=null;Toast.makeText(this,"悬浮摇杆显示失败，请检查悬浮窗权限",Toast.LENGTH_LONG).show();}
    }
    private void addSpeed(LinearLayout row,String text,double value,String name){Button button=new Button(this);button.setText(text);button.setTextSize(12);button.setPadding(0,0,0,0);button.setMinWidth(0);button.setOnClickListener(v->{synchronized(lock){joySpeed=value;}speedLabel.setText(name);});row.addView(button,new LinearLayout.LayoutParams(dp(38),dp(38)));}
    private void hideControls(){synchronized(lock){if(joyStrength>0){points.clear();points.add(new double[]{latitude,longitude});routeLength=0;routeStarted=SystemClock.elapsedRealtime();lastTick=routeStarted;}joyStrength=0;}if(controls!=null){try{windowManager.removeView(controls);}catch(Exception ignored){}controls=null;}}
    @Override public void onDestroy(){synchronized(lock){running=false;joyStrength=0;}if(worker!=null)worker.removeCallbacksAndMessages(null);hideControls();if(gps)removeProvider(LocationManager.GPS_PROVIDER);if(network)removeProvider(LocationManager.NETWORK_PROVIDER);if(locationThread!=null)locationThread.quitSafely();if(!failed)updateStatus("模拟已停止",false);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return binder;}
    public class ServiceGoBinder extends Binder {public void setPosition(double lng,double lat,double alt){try{validate(lat,lng);altitude=alt;ArrayList<double[]> p=new ArrayList<>();p.add(new double[]{lat,lng});worker.post(()->startPosition(p,1.2));}catch(Exception e){updateStatus("坐标无效",false);}}}
}
