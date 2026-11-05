package com.zcshou.gogogo;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.annotations.PolylineOptions;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.json.*;
import com.zcshou.utils.MapUtils;
import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;

/** Key-free map and WGS-84 route editor. */
public class FreeMapActivity extends AppCompatActivity {
    private MapView mapView; private MapLibreMap map; private TextView info; private LatLng selected;
    private final ArrayList<LatLng> route=new ArrayList<>(); private boolean drawing;
    private final OkHttpClient http=new OkHttpClient(); private Call searchCall; private int generation;
    private android.content.SharedPreferences prefs;
    private static final String STYLE="https://tiles.openfreemap.org/styles/liberty";
    @Override public void onCreate(Bundle saved){super.onCreate(saved);MapLibre.getInstance(this);prefs=getSharedPreferences("free_map",MODE_PRIVATE);migrateFavorites();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this);title.setText("位置模拟 · OpenStreetMap");title.setTextSize(20);title.setTextColor(Color.WHITE);title.setBackgroundColor(0xff008577);title.setPadding(16,18,16,18);root.addView(title);
        info=new TextView(this);info.setText("点击地图选点；路线模式下依次点击添加节点");info.setPadding(12,8,12,8);root.addView(info);
        mapView=new MapView(this);mapView.onCreate(saved);root.addView(mapView,new LinearLayout.LayoutParams(-1,0,1));
        HorizontalScrollView scroll=new HorizontalScrollView(this);LinearLayout bar=new LinearLayout(this);scroll.addView(bar);root.addView(scroll);setContentView(root);
        addButton(bar,"搜索",this::searchDialog);addButton(bar,"传送",()->{if(selected==null){toast("请先选择位置");return;}startSimulation(single(selected),1.4);});
        addButton(bar,"收藏",this::addFavorite);addButton(bar,"收藏夹",this::favorites);
        addButton(bar,"画路线",()->{drawing=!drawing;if(drawing)route.clear(); redraw();toast(drawing?"路线绘制中，依次点击地图添加节点":"路线绘制已结束");});
        addButton(bar,"撤销节点",()->{if(!route.isEmpty()){route.remove(route.size()-1);redraw();}});
        addButton(bar,"保存路线",this::saveRoute);addButton(bar,"路线列表",this::routes);addButton(bar,"开始路线",this::playRoute);
        addButton(bar,"停止模拟",()->{startService(new Intent(this,FreeSimulationService.class).setAction(FreeSimulationService.ACTION_STOP));toast("已请求停止模拟");});
        addButton(bar,"更新",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/xieyinch/location-simulator/releases"))));
        mapView.getMapAsync(m->{map=m;map.setStyle(STYLE,style->{map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(34.23,108.94),12));redraw();});
            map.addOnMapClickListener(p->{selected=p;if(drawing)route.add(p);redraw();info.setText(String.format(java.util.Locale.US,"%.6f, %.6f%s",p.getLatitude(),p.getLongitude(),drawing?"  ·  节点 "+route.size():""));return true;});});
    }
    private void addButton(LinearLayout bar,String text,Runnable r){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setOnClickListener(v->r.run());bar.addView(b);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void redraw(){if(map==null||map.getStyle()==null)return;map.clear();if(route.size()>1)map.addPolyline(new PolylineOptions().addAll(route).color(0xff008577).width(5));for(LatLng p:route)map.addMarker(new MarkerOptions().position(p));if(selected!=null)map.addMarker(new MarkerOptions().position(selected).title("选中位置"));}
    private JSONArray read(String key){try{return new JSONArray(prefs.getString(key,"[]"));}catch(JSONException e){return new JSONArray();}}
    private boolean write(String key,JSONArray a){return prefs.edit().putString(key,a.toString()).commit();}
    private JSONObject point(LatLng p)throws JSONException{return new JSONObject().put("lat",p.getLatitude()).put("lon",p.getLongitude());}
    private JSONArray single(LatLng p){JSONArray a=new JSONArray();try{a.put(point(p));}catch(JSONException ignored){}return a;}
    private void nameDialog(String title,java.util.function.Consumer<String> accept){EditText input=new EditText(this);input.setSingleLine();input.setHint("请输入名称");new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty()){toast("名称不能为空");return;}accept.accept(name);}).show();}
    private void addFavorite(){if(selected==null){toast("请先选择位置");return;}LatLng p=selected;nameDialog("收藏位置",name->{try{JSONArray a=read("favorites");JSONObject x=point(p);x.put("name",name);a.put(x);toast(write("favorites",a)?"已收藏":"保存失败");}catch(JSONException e){toast("保存失败");}});}
    private void favorites(){JSONArray a=read("favorites");if(a.length()==0){toast("还没有收藏");return;}String[] names=new String[a.length()];for(int i=0;i<a.length();i++)names[i]=a.optJSONObject(i).optString("name","未命名");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("收藏位置：点击传送，长按删除").setItems(names,(d,i)->{JSONObject p=a.optJSONObject(i);if(p==null)return;selected=new LatLng(p.optDouble("lat"),p.optDouble("lon"));if(map!=null)map.animateCamera(CameraUpdateFactory.newLatLngZoom(selected,16));redraw();startSimulation(single(selected),1.4);}).setNegativeButton("关闭",null).create();
        dialog.setOnShowListener(d->dialog.getListView().setOnItemLongClickListener((parent,view,i,id)->{confirmDelete("favorites",a,i,dialog);return true;}));dialog.show();}
    private void saveRoute(){if(route.size()<2){toast("至少需要两个路线节点");return;}JSONArray nodes=routeJson();nameDialog("保存路线",name->{try{JSONArray a=read("routes");a.put(new JSONObject().put("name",name).put("points",nodes));toast(write("routes",a)?"路线已保存":"保存失败");}catch(JSONException e){toast("保存失败");}});}
    private JSONArray routeJson(){JSONArray a=new JSONArray();for(LatLng p:route)try{a.put(point(p));}catch(JSONException ignored){}return a;}
    private void routes(){JSONArray a=read("routes");if(a.length()==0){toast("还没有保存路线");return;}String[] names=new String[a.length()];for(int i=0;i<a.length();i++)names[i]=a.optJSONObject(i).optString("name","未命名");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择路线：点击载入，长按删除").setItems(names,(d,i)->{JSONArray nodes=a.optJSONObject(i).optJSONArray("points");if(nodes==null)return;route.clear();for(int n=0;n<nodes.length();n++){JSONObject p=nodes.optJSONObject(n);if(p!=null)route.add(new LatLng(p.optDouble("lat"),p.optDouble("lon")));}drawing=false;if(!route.isEmpty()&&map!=null)map.animateCamera(CameraUpdateFactory.newLatLngZoom(route.get(0),16));redraw();toast("路线已载入，点击开始路线回放");}).setNegativeButton("关闭",null).create();
        dialog.setOnShowListener(d->dialog.getListView().setOnItemLongClickListener((parent,view,i,id)->{confirmDelete("routes",a,i,dialog);return true;}));dialog.show();}
    private void confirmDelete(String key,JSONArray a,int index,AlertDialog list){new AlertDialog.Builder(this).setMessage("确定删除？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{JSONArray b=new JSONArray();for(int i=0;i<a.length();i++)if(i!=index)b.put(a.opt(i));if(write(key,b)){list.dismiss();toast("已删除");}else toast("删除失败");}).show();}
    private void playRoute(){if(route.size()<2){toast("请先绘制或载入至少两个节点的路线");return;}EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setText("1.4");new AlertDialog.Builder(this).setTitle("设置回放速度（米/秒）").setView(input).setNegativeButton("取消",null).setPositiveButton("开始",(d,w)->{try{double speed=Double.parseDouble(input.getText().toString());if(speed<=0||speed>100||!Double.isFinite(speed))throw new IllegalArgumentException();startSimulation(routeJson(),speed);}catch(Exception e){toast("请输入大于 0 且不超过 100 的速度");}}).show();}
    private void startSimulation(JSONArray points,double speed){AppOpsManager ops=(AppOpsManager)getSystemService(APP_OPS_SERVICE);int mode=ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,android.os.Process.myUid(),getPackageName());if(mode!=AppOpsManager.MODE_ALLOWED){new AlertDialog.Builder(this).setTitle("选择模拟位置应用").setMessage("请在开发者选项中，将本应用设为模拟位置信息应用，然后返回重试。") .setNegativeButton("取消",null).setPositiveButton("打开设置",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))).show();return;}
        Intent i=new Intent(this,FreeSimulationService.class).putExtra("points",points.toString()).putExtra("speed",speed);try{startForegroundService(i);toast("已请求启动位置模拟，请查看通知栏确认");}catch(Exception e){toast("启动失败："+e.getMessage());}}
    private void searchDialog(){EditText input=new EditText(this);input.setSingleLine();input.setHint("例如：西安建筑科技大学草堂校区");new AlertDialog.Builder(this).setTitle("搜索地点（Photon / OSM）").setView(input).setNegativeButton("取消",null).setPositiveButton("搜索",(d,w)->search(input.getText().toString().trim())).show();}
    private void search(String q){if(q.isEmpty())return;if(searchCall!=null)searchCall.cancel();int id=++generation;HttpUrl url=HttpUrl.parse("https://photon.komoot.io/api/").newBuilder().addQueryParameter("q",q).addQueryParameter("limit","20").build();searchCall=http.newCall(new Request.Builder().url(url).header("User-Agent","LocationSimulator-Android/1.0 (github.com/xieyinch/location-simulator)").build());toast("正在搜索…");searchCall.enqueue(new Callback(){public void onFailure(Call c,IOException e){if(!c.isCanceled())runOnUiThread(()->{if(id==generation&&!isFinishing())toast("搜索连接失败，请检查网络");});}public void onResponse(Call c,Response res){try(Response response=res){if(!response.isSuccessful()||response.body()==null)throw new IOException();JSONArray features=new JSONObject(response.body().string()).getJSONArray("features");ArrayList<LatLng> locs=new ArrayList<>();ArrayList<String> names=new ArrayList<>();for(int i=0;i<features.length();i++){JSONObject f=features.getJSONObject(i),prop=f.optJSONObject("properties"),geo=f.optJSONObject("geometry");if(prop==null||geo==null)continue;JSONArray coord=geo.optJSONArray("coordinates");if(coord==null||coord.length()<2)continue;double lon=coord.optDouble(0,Double.NaN),lat=coord.optDouble(1,Double.NaN);if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)continue;locs.add(new LatLng(lat,lon));names.add(prop.optString("name",q)+"  "+prop.optString("city","")+" "+prop.optString("state",""));}runOnUiThread(()->{if(id!=generation||isFinishing())return;if(names.isEmpty()){toast("没有找到结果，试试其他关键词");return;}new AlertDialog.Builder(FreeMapActivity.this).setTitle("搜索结果").setItems(names.toArray(new String[0]),(d,n)->{selected=locs.get(n);if(map!=null)map.animateCamera(CameraUpdateFactory.newLatLngZoom(selected,16));redraw();}).setNegativeButton("关闭",null).show();});}catch(Exception e){runOnUiThread(()->{if(id==generation&&!isFinishing())toast("搜索服务暂时不可用");});}}});}
    private void migrateFavorites(){if(prefs.getBoolean("migrated",false))return;try{JSONArray old=new JSONArray(getSharedPreferences("MainActivity",MODE_PRIVATE).getString("favorite_locations","[]"));JSONArray a=read("favorites");for(int i=0;i<old.length();i++){JSONObject p=old.optJSONObject(i);if(p==null)continue;double[] w=MapUtils.bd2wgs(p.getDouble("lng"),p.getDouble("lat"));a.put(new JSONObject().put("name",p.optString("name","旧收藏")).put("lat",w[1]).put("lon",w[0]));}prefs.edit().putString("favorites",a.toString()).putBoolean("migrated",true).apply();}catch(Exception e){android.util.Log.e("FreeMap","Favorite migration failed",e);}}
    @Override protected void onStart(){super.onStart();mapView.onStart();} @Override protected void onResume(){super.onResume();mapView.onResume();}
    @Override protected void onPause(){mapView.onPause();super.onPause();} @Override protected void onStop(){mapView.onStop();super.onStop();}
    @Override protected void onDestroy(){if(searchCall!=null)searchCall.cancel();mapView.onDestroy();super.onDestroy();}
    @Override public void onLowMemory(){super.onLowMemory();mapView.onLowMemory();} @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);mapView.onSaveInstanceState(out);}
}
