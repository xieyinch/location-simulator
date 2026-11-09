package com.zcshou.gogogo;

import android.app.AppOpsManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.ServiceSettings;
import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.InputtipsQuery;
import com.amap.api.services.help.Tip;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkPath;
import com.amap.api.services.route.WalkRouteResult;
import com.amap.api.services.route.WalkStep;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Map and route interaction adapted from Lecheeel/Portal; positions stored as WGS-84. */
public class FreeMapActivity extends AppCompatActivity {
    private static final int BLUE=0xff007aff, TEXT=0xff1c1c1e, MUTED=0xff6e6e73;
    private MapView mapView; private AMap map; private FrameLayout mapContainer;
    private TextView info; private SharedPreferences prefs; private double[] selected;
    private final ArrayList<double[]> route=new ArrayList<>();
    private final ArrayList<Integer> undo=new ArrayList<>();
    private int mode=0, revision=0, searchGeneration=0; // 0 select, 1 waypoint, 2 pen
    private float lastX,lastY; private boolean penActive,planning,resumed;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=getSharedPreferences("free_map",MODE_PRIVATE);
        if(saved!=null) restoreState(saved);
        getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff2f2f7);
        LinearLayout header=new LinearLayout(this);header.setPadding(dp(20),dp(12),dp(20),dp(10));header.setGravity(Gravity.CENTER_VERTICAL);header.setBackgroundColor(Color.WHITE);
        LinearLayout headings=new LinearLayout(this);headings.setOrientation(LinearLayout.VERTICAL);
        TextView small=label("EXPLORE",11,MUTED);small.setLetterSpacing(.15f);headings.addView(small);
        headings.addView(label("位置模拟",25,TEXT));header.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        Button search=button("⌕  搜索",false);search.setOnClickListener(v->searchDialog());header.addView(search);root.addView(header);
        mapContainer=new FrameLayout(this);root.addView(mapContainer,new LinearLayout.LayoutParams(-1,0,1));
        TextView mapHint=label("设置高德 Key 后加载地图",15,MUTED);mapHint.setGravity(Gravity.CENTER);mapContainer.addView(mapHint,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(12),dp(16),dp(10));card.setBackground(round(Color.WHITE,20));
        LinearLayout.LayoutParams cardP=new LinearLayout.LayoutParams(-1,-2);cardP.setMargins(dp(12),dp(10),dp(12),dp(8));root.addView(card,cardP);
        TextView caption=label("当前选择",12,MUTED);card.addView(caption);info=label("点击地图选择位置",16,TEXT);info.setPadding(0,dp(4),0,dp(10));card.addView(info);
        LinearLayout primary=new LinearLayout(this);Button go=button("开始模拟",true);go.setOnClickListener(v->{if(selected==null){toast("请先选择位置");return;}startSimulation(single(selected),1.4);});
        primary.addView(go,new LinearLayout.LayoutParams(0,dp(46),1));Button save=button("♡ 收藏",false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(110),dp(46));sp.leftMargin=dp(8);primary.addView(save,sp);save.setOnClickListener(v->addFavorite());card.addView(primary);
        LinearLayout nav=new LinearLayout(this);nav.setBackgroundColor(Color.WHITE);nav.setPadding(dp(8),dp(5),dp(8),dp(6));
        addNav(nav,"地图",()->{mode=0;updateInfo();});addNav(nav,"路线",this::routeMenu);addNav(nav,"收藏",this::favorites);addNav(nav,"设置",this::settingsMenu);root.addView(nav);
        setContentView(root);updateInfo();
        String key=mapKey();if(!key.isEmpty() && prefs.getBoolean("amap_consent",false)) initializeMap(saved,key);
        else if(!key.isEmpty()) askPrivacy(saved,key);else mapContainer.post(()->keyDialog(true));
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);return v;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Button button(String text,boolean solid){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(solid?Color.WHITE:BLUE);b.setBackground(round(solid?BLUE:0xffeef5ff,14));b.setPadding(dp(12),0,dp(12),0);return b;}
    private void addNav(LinearLayout row,String title,Runnable action){Button b=button(title,false);b.setBackgroundColor(Color.TRANSPARENT);b.setOnClickListener(v->action.run());row.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private String mapKey(){String local=prefs.getString("amap_key","");if(local!=null&&!local.trim().isEmpty())return local.trim();try{ApplicationInfo a=getPackageManager().getApplicationInfo(getPackageName(),PackageManager.GET_META_DATA);return a.metaData==null?"":a.metaData.getString("com.amap.api.v2.apikey","");}catch(Exception e){return "";}}
    private String signingSha1(){try{android.content.pm.PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_SIGNATURES);byte[] hash=java.security.MessageDigest.getInstance("SHA-1").digest(p.signatures[0].toByteArray());StringBuilder b=new StringBuilder();for(byte v:hash){if(b.length()>0)b.append(':');b.append(String.format(Locale.US,"%02X",v&255));}return b.toString();}catch(Exception e){return "无法获取";}}
    private void keyDialog(boolean first){EditText edit=new EditText(this);edit.setSingleLine();edit.setHint("高德 Android SDK Key");String local=prefs.getString("amap_key","");if(local!=null)edit.setText(local);
        new AlertDialog.Builder(this).setTitle("配置高德地图")
                .setMessage("地图、搜索和步行规划使用高德 SDK。请填写绑定本应用包名和签名 SHA-1 的 Android Key。包名："+getPackageName()+"\n签名 SHA-1："+signingSha1()+"\n\n你也可以先取消，手动输入坐标使用模拟定位。")
                .setView(edit).setNegativeButton(first?"稍后":"取消",null)
                .setNeutralButton("申请 Key",(d,w)->openUrl("https://console.amap.com/"))
                .setPositiveButton("保存",(d,w)->{String key=edit.getText().toString().trim();if(key.isEmpty()){toast("Key 不能为空");return;}prefs.edit().putString("amap_key",key).apply();recreate();}).show();}
    private void askPrivacy(Bundle state,String key){new AlertDialog.Builder(this).setTitle("使用高德地图")
            .setMessage("地图、地点搜索及路线规划由高德提供，使用时高德 SDK 可能处理设备信息、网络信息和位置信息。选择同意后才会初始化 SDK；你可以先查看高德隐私政策。")
            .setNeutralButton("隐私政策",(d,w)->{openUrl("https://lbs.amap.com/pages/privacy/");})
            .setNegativeButton("暂不使用",null).setPositiveButton("同意并继续",(d,w)->{prefs.edit().putBoolean("amap_consent",true).apply();initializeMap(state,key);}).show();}
    private void initializeMap(Bundle state,String key){try{
        MapsInitializer.updatePrivacyShow(getApplicationContext(),true,true);MapsInitializer.updatePrivacyAgree(getApplicationContext(),true);
        ServiceSettings.updatePrivacyShow(getApplicationContext(),true,true);ServiceSettings.updatePrivacyAgree(getApplicationContext(),true);
        MapsInitializer.setApiKey(key);ServiceSettings.getInstance().setApiKey(key);
        mapView=new MapView(this);mapView.onCreate(state);if(resumed)mapView.onResume();mapContainer.removeAllViews();mapContainer.addView(mapView,new FrameLayout.LayoutParams(-1,-1));map=mapView.getMap();
        map.getUiSettings().setZoomControlsEnabled(false);map.getUiSettings().setScaleControlsEnabled(true);
        if(selected==null)map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(34.23,108.94),12));else moveTo(selected);
        map.setOnMapClickListener(p->{if(mode==2)return;double[] w=MapCoordinates.toWgs(p.latitude,p.longitude);selected=w;if(mode==1){undo.add(route.size());route.add(w.clone());revision++;}redraw();updateInfo();});
        mapView.setOnTouchListener((v,event)->handlePen(event));redraw();
    }catch(Exception e){android.util.Log.e("LocationMap","AMap initialization failed",e);toast("地图初始化失败，请检查 Key 和网络");}}
    private boolean handlePen(MotionEvent event){if(mode!=2||map==null)return false;if(event.getPointerCount()>1){penActive=false;return true;}int a=event.getActionMasked();
        if(a==MotionEvent.ACTION_DOWN){undo.add(route.size());penActive=true;lastX=event.getX();lastY=event.getY();appendPen(lastX,lastY);return true;}
        if(a==MotionEvent.ACTION_MOVE&&penActive){float x=event.getX(),y=event.getY();if(Math.hypot(x-lastX,y-lastY)>=dp(8)){lastX=x;lastY=y;appendPen(x,y);}return true;}
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){penActive=false;return true;}return true;}
    private void appendPen(float x,float y){if(route.size()>=5000){toast("路线节点已达上限");penActive=false;return;}LatLng p=map.getProjection().fromScreenLocation(new Point((int)x,(int)y));if(p==null)return;double[] w=MapCoordinates.toWgs(p.latitude,p.longitude);route.add(w);selected=w;revision++;redraw();updateInfo();}
    private LatLng display(double[] w){double[] p=MapCoordinates.toGcj(w[0],w[1]);return new LatLng(p[0],p[1]);}
    private void redraw(){if(map==null)return;map.clear();if(route.size()>1){ArrayList<LatLng> line=new ArrayList<>();for(double[] p:route)line.add(display(p));map.addPolyline(new PolylineOptions().addAll(line).color(BLUE).width(dp(5)));map.addMarker(new MarkerOptions().position(line.get(0)).title("起点"));map.addMarker(new MarkerOptions().position(line.get(line.size()-1)).title("终点"));}else if(selected!=null)map.addMarker(new MarkerOptions().position(display(selected)).title("选中位置"));}
    private void moveTo(double[] p){if(map!=null)map.animateCamera(CameraUpdateFactory.newLatLngZoom(display(p),16));}
    private void updateInfo(){if(info==null)return;String selection=selected==null?"点击地图选择位置":String.format(Locale.US,"%.6f, %.6f",selected[0],selected[1]);String state=mode==1?" · 逐点编辑":mode==2?" · 手绘中":"";info.setText(selection+state+(route.isEmpty()?"":" · "+route.size()+" 个路线点"));}
    private void searchDialog(){if(map==null){toast("请先配置高德地图 Key 并同意隐私政策");return;}EditText input=new EditText(this);input.setSingleLine();input.setHint("输入地点或地址");new AlertDialog.Builder(this).setTitle("搜索地点").setView(input).setNegativeButton("取消",null).setPositiveButton("搜索",(d,w)->search(input.getText().toString().trim())).show();}
    private void search(String q){if(q.isEmpty())return;final int generation=++searchGeneration;try{Inputtips tips=new Inputtips(this,new InputtipsQuery(q,""));tips.setInputtipsListener((items,code)->{if(isFinishing()||generation!=searchGeneration)return;if(code!=1000){toast("搜索失败（"+code+"），请检查 Key 授权或网络");return;}ArrayList<String> names=new ArrayList<>();ArrayList<double[]> positions=new ArrayList<>();if(items!=null)for(Tip t:items){if(t==null||t.getPoint()==null)continue;positions.add(MapCoordinates.toWgs(t.getPoint().getLatitude(),t.getPoint().getLongitude()));names.add((t.getName()==null?"地点":t.getName())+"\n"+(t.getAddress()==null?"":t.getAddress()));}if(names.isEmpty()){toast("没有找到结果，请尝试其他关键词");return;}new AlertDialog.Builder(this).setTitle("搜索结果").setItems(names.toArray(new String[0]),(d,i)->{selected=positions.get(i);moveTo(selected);redraw();updateInfo();}).setNegativeButton("关闭",null).show();});tips.requestInputtipsAsyn();}catch(Exception e){toast("搜索初始化失败，请检查高德 Key");}}
    private void routeMenu(){String[] options={"逐点绘制","手指画线","完成绘制","撤销上一次","清空路线","步行道路规划（首点至末点）","保存路线","路线库","开始路线模拟","停止模拟"};new AlertDialog.Builder(this).setTitle("路线").setItems(options,(d,n)->{switch(n){case 0:mode=1;updateInfo();toast("逐点点击地图添加路线");break;case 1:mode=2;updateInfo();toast("在地图上拖动手指画线；结束后可移动地图");break;case 2:mode=0;updateInfo();break;case 3:undoRoute();break;case 4:new AlertDialog.Builder(this).setMessage("清空当前路线？").setNegativeButton("取消",null).setPositiveButton("清空",(a,b)->{route.clear();undo.clear();revision++;redraw();updateInfo();}).show();break;case 5:planWalk();break;case 6:saveRoute();break;case 7:routes();break;case 8:playRoute();break;case 9:stopSimulation();break;}}).setNegativeButton("关闭",null).show();}
    private void undoRoute(){if(undo.isEmpty()){toast("没有可撤销的节点");return;}int size=undo.remove(undo.size()-1);while(route.size()>size)route.remove(route.size()-1);revision++;redraw();updateInfo();}
    // Adapted from Lecheeel/Portal AMapRoutePlanner: each request owns its callback.
    private void planWalk(){if(map==null){toast("请先配置高德地图 Key");return;}if(route.size()<2){toast("请先添加起点和终点");return;}if(planning){toast("正在规划路线");return;}final int expected=revision;double[] start=MapCoordinates.toGcj(route.get(0)[0],route.get(0)[1]),end=MapCoordinates.toGcj(route.get(route.size()-1)[0],route.get(route.size()-1)[1]);planning=true;
        try{RouteSearch search=new RouteSearch(this);search.setRouteSearchListener(new RouteSearch.OnRouteSearchListener(){
            @Override public void onWalkRouteSearched(WalkRouteResult result,int code){planning=false;if(isFinishing())return;if(expected!=revision){toast("路线已变化，规划结果已忽略");return;}if(code!=1000||result==null||result.getPaths()==null||result.getPaths().isEmpty()){toast("步行规划失败（"+code+"），请检查网络与高德 Key 权限");return;}WalkPath path=result.getPaths().get(0);ArrayList<double[]> points=new ArrayList<>();if(path.getSteps()!=null)for(WalkStep step:path.getSteps())if(step.getPolyline()!=null)for(LatLonPoint p:step.getPolyline())points.add(MapCoordinates.toWgs(p.getLatitude(),p.getLongitude()));if(points.size()<2||points.size()>5000){toast("规划结果无效或路线过长");return;}route.clear();route.addAll(points);undo.clear();revision++;mode=0;selected=route.get(route.size()-1).clone();redraw();updateInfo();toast("步行路线已规划，可保存或开始模拟");}
            @Override public void onBusRouteSearched(BusRouteResult r,int c){} @Override public void onDriveRouteSearched(DriveRouteResult r,int c){} @Override public void onRideRouteSearched(RideRouteResult r,int c){}
        });RouteSearch.FromAndTo endpoints=new RouteSearch.FromAndTo(new LatLonPoint(start[0],start[1]),new LatLonPoint(end[0],end[1]));search.calculateWalkRouteAsyn(new RouteSearch.WalkRouteQuery(endpoints));toast("正在规划步行路线…");}catch(Exception e){planning=false;toast("规划初始化失败，请检查 Key 和网络");}}
    private JSONArray read(String key){try{return new JSONArray(prefs.getString(key,"[]"));}catch(JSONException e){return new JSONArray();}}
    private boolean write(String key,JSONArray a){return prefs.edit().putString(key,a.toString()).commit();}
    private JSONObject point(double[] p)throws JSONException{return new JSONObject().put("lat",p[0]).put("lon",p[1]);}
    private JSONArray single(double[] p){JSONArray a=new JSONArray();try{a.put(point(p));}catch(JSONException ignored){}return a;}
    private JSONArray routeJson(){JSONArray a=new JSONArray();for(double[] p:route)try{a.put(point(p));}catch(JSONException ignored){}return a;}
    private void nameDialog(String title,java.util.function.Consumer<String> accept){EditText input=new EditText(this);input.setSingleLine();input.setHint("请输入名称");new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty()){toast("名称不能为空");return;}accept.accept(name);}).show();}
    private void addFavorite(){if(selected==null){toast("请先选择位置");return;}double[] p=selected.clone();nameDialog("收藏位置",name->{try{JSONArray a=read("favorites");a.put(point(p).put("name",name));toast(write("favorites",a)?"已收藏":"保存失败");}catch(JSONException e){toast("保存失败");}});}
    private void favorites(){JSONArray a=read("favorites");if(a.length()==0){toast("还没有收藏");return;}String[] names=new String[a.length()];for(int i=0;i<a.length();i++)names[i]=a.optJSONObject(i).optString("name","未命名");AlertDialog dialog=new AlertDialog.Builder(this).setTitle("收藏位置：点击传送，长按删除").setItems(names,(d,i)->{JSONObject p=a.optJSONObject(i);if(p==null)return;selected=new double[]{p.optDouble("lat"),p.optDouble("lon")};moveTo(selected);redraw();updateInfo();startSimulation(single(selected),1.4);}).setNegativeButton("关闭",null).create();dialog.setOnShowListener(d->dialog.getListView().setOnItemLongClickListener((parent,view,i,id)->{confirmDelete("favorites",a,i,dialog);return true;}));dialog.show();}
    private void saveRoute(){if(route.size()<2){toast("至少需要两个路线节点");return;}JSONArray nodes=routeJson();nameDialog("保存路线",name->{try{JSONArray a=read("routes");a.put(new JSONObject().put("name",name).put("points",nodes));toast(write("routes",a)?"路线已保存":"保存失败");}catch(JSONException e){toast("保存失败");}});}
    private void routes(){JSONArray a=read("routes");if(a.length()==0){toast("还没有保存路线");return;}String[] names=new String[a.length()];for(int i=0;i<a.length();i++)names[i]=a.optJSONObject(i).optString("name","未命名");AlertDialog dialog=new AlertDialog.Builder(this).setTitle("路线库：点击载入，长按删除").setItems(names,(d,i)->{JSONArray nodes=a.optJSONObject(i).optJSONArray("points");if(nodes==null)return;route.clear();undo.clear();for(int n=0;n<nodes.length();n++){JSONObject p=nodes.optJSONObject(n);if(p!=null)route.add(new double[]{p.optDouble("lat"),p.optDouble("lon")});}revision++;mode=0;if(!route.isEmpty()){selected=route.get(0).clone();moveTo(selected);}redraw();updateInfo();toast("路线已载入，可开始模拟");}).setNegativeButton("关闭",null).create();dialog.setOnShowListener(d->dialog.getListView().setOnItemLongClickListener((parent,view,i,id)->{confirmDelete("routes",a,i,dialog);return true;}));dialog.show();}
    private void confirmDelete(String key,JSONArray a,int index,AlertDialog list){new AlertDialog.Builder(this).setMessage("确定删除？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{JSONArray b=new JSONArray();for(int i=0;i<a.length();i++)if(i!=index)b.put(a.opt(i));if(write(key,b)){list.dismiss();toast("已删除");}else toast("删除失败");}).show();}
    private void playRoute(){if(route.size()<2){toast("请先绘制或载入路线");return;}EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setText("1.4");new AlertDialog.Builder(this).setTitle("设置回放速度（米/秒）").setView(input).setNegativeButton("取消",null).setPositiveButton("开始",(d,w)->{try{double speed=Double.parseDouble(input.getText().toString());if(speed<=0||speed>100||!Double.isFinite(speed))throw new IllegalArgumentException();startSimulation(routeJson(),speed);}catch(Exception e){toast("请输入大于 0 且不超过 100 的速度");}}).show();}
    private void startSimulation(JSONArray points,double speed){AppOpsManager ops=(AppOpsManager)getSystemService(APP_OPS_SERVICE);int op=ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,android.os.Process.myUid(),getPackageName());if(op!=AppOpsManager.MODE_ALLOWED){new AlertDialog.Builder(this).setTitle("选择模拟位置应用").setMessage("请在开发者选项中，将本应用设为模拟位置信息应用，然后返回重试。") .setNegativeButton("取消",null).setPositiveButton("打开设置",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))).show();return;}try{startForegroundService(new Intent(this,FreeSimulationService.class).putExtra("points",points.toString()).putExtra("speed",speed));toast("已请求启动模拟，请查看通知确认");}catch(Exception e){toast("启动失败："+e.getMessage());}}
    private void stopSimulation(){try{stopService(new Intent(this,FreeSimulationService.class));toast("模拟已停止");}catch(Exception e){toast("停止失败");}}
    private void settingsMenu(){String[] options={"配置高德地图 Key","手动输入坐标","显示悬浮方向键","停止模拟","检查本项目更新","高德隐私政策"};new AlertDialog.Builder(this).setTitle("设置").setItems(options,(d,n)->{switch(n){case 0:keyDialog(false);break;case 1:manualCoordinate();break;case 2:showOverlay();break;case 3:stopSimulation();break;case 4:openUrl("https://github.com/xieyinch/location-simulator/releases");break;case 5:openUrl("https://lbs.amap.com/pages/privacy/");break;}}).setNegativeButton("关闭",null).show();}
    private void manualCoordinate(){LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);EditText lat=new EditText(this),lon=new EditText(this);lat.setHint("纬度 WGS-84");lon.setHint("经度 WGS-84");lat.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);lon.setInputType(lat.getInputType());form.addView(lat);form.addView(lon);new AlertDialog.Builder(this).setTitle("输入坐标").setView(form).setNegativeButton("取消",null).setPositiveButton("选择",(d,w)->{try{double a=Double.parseDouble(lat.getText().toString()),b=Double.parseDouble(lon.getText().toString());if(!Double.isFinite(a)||!Double.isFinite(b)||Math.abs(a)>90||Math.abs(b)>180)throw new IllegalArgumentException();selected=new double[]{a,b};moveTo(selected);redraw();updateInfo();}catch(Exception e){toast("坐标格式或范围有误");}}).show();}
    private void showOverlay(){if(!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setMessage("请先授权显示悬浮窗，再返回点击悬浮方向键。").setNegativeButton("取消",null).setPositiveButton("授权",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).show();return;}try{startForegroundService(new Intent(this,FreeSimulationService.class).setAction(FreeSimulationService.ACTION_SHOW_CONTROLS));}catch(Exception e){toast("请先启动位置模拟");}}
    private void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("无法打开浏览器");}}
    private void restoreState(Bundle state){mode=state.getInt("mode",0);try{JSONArray a=new JSONArray(state.getString("route","[]"));for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);route.add(new double[]{p.getDouble("lat"),p.getDouble("lon")});}String v=state.getString("selected");if(v!=null){JSONObject p=new JSONObject(v);selected=new double[]{p.getDouble("lat"),p.getDouble("lon")};}}catch(JSONException ignored){}}
    @Override protected void onResume(){super.onResume();resumed=true;if(mapView!=null)mapView.onResume();} @Override protected void onPause(){resumed=false;if(mapView!=null)mapView.onPause();super.onPause();}
    @Override protected void onDestroy(){searchGeneration++;revision++;if(mapView!=null)mapView.onDestroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("mode",mode);out.putString("route",routeJson().toString());if(selected!=null)try{out.putString("selected",point(selected).toString());}catch(JSONException ignored){}if(mapView!=null)mapView.onSaveInstanceState(out);}
}
