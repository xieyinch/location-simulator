package io.github.locationsimulator.probe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import java.util.Locale;

/** Independent app/UID observes the platform updates like a third-party consumer. */
public final class ProbeActivity extends Activity {
    private LocationManager manager;
    private final android.os.Handler handler=new android.os.Handler(Looper.getMainLooper());
    private boolean networkListening;
    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            Log.i("LocationProbe", String.format(Locale.US, "%s %.5f %.5f mock=%s",location.getProvider(), location.getLatitude(), location.getLongitude(), location.isFromMockProvider()));
        }
        @Override public void onStatusChanged(String provider,int status,Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };
    @SuppressLint("MissingPermission")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,listener,Looper.getMainLooper());
            subscribeNetwork();
            Log.i("LocationProbe", "READY");
        } catch (RuntimeException error) { Log.e("LocationProbe", "LISTENER_ERROR", error); throw error; }
    }
    @SuppressLint("MissingPermission")
    private void subscribeNetwork() {
        if(networkListening)return;
        try { manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,0,0,listener,Looper.getMainLooper());networkListening=true;Log.i("LocationProbe", "NETWORK_READY"); }
        catch (IllegalArgumentException missing) { handler.postDelayed(this::subscribeNetwork,100); }
    }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null);if(manager!=null)manager.removeUpdates(listener);super.onDestroy(); }
}
