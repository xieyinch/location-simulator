package com.zcshou.gogogo;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.zcshou.service.ServiceGo;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class MockAuthorizationInstrumentedTest {
    @Test public void revokedMockPermissionIsReported() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("appops set io.github.xieyinch.locationsimulator android:mock_location allow").close();
            ContextCompat.startForegroundService(context,new Intent(context,ServiceGo.class).putExtra("points","[{\"lat\":34.23,\"lon\":108.94}]").putExtra("speed",1.2));
            long deadline=SystemClock.elapsedRealtime()+10000;
            while(SystemClock.elapsedRealtime()<deadline && !context.getSharedPreferences(ServiceGo.STATUS_PREFS,Context.MODE_PRIVATE).getBoolean("active",false)) SystemClock.sleep(100);
            assertTrue("模拟未启动",context.getSharedPreferences(ServiceGo.STATUS_PREFS,Context.MODE_PRIVATE).getBoolean("active",false));
            InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("appops set io.github.xieyinch.locationsimulator android:mock_location ignore").close();
            deadline=SystemClock.elapsedRealtime()+5000;
            while(SystemClock.elapsedRealtime()<deadline && !context.getSharedPreferences(ServiceGo.STATUS_PREFS,Context.MODE_PRIVATE).getString("text","").contains("授权已失效")) SystemClock.sleep(100);
            assertTrue("授权被取消后没有报告失败",context.getSharedPreferences(ServiceGo.STATUS_PREFS,Context.MODE_PRIVATE).getString("text","").contains("授权已失效"));
            assertFalse(context.getSharedPreferences(ServiceGo.STATUS_PREFS,Context.MODE_PRIVATE).getBoolean("active",true));
        } finally {
            context.stopService(new Intent(context,ServiceGo.class));
            InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("appops set io.github.xieyinch.locationsimulator android:mock_location allow").close();
        }
    }
}
