package io.github.tehcneko.clipboardwhitelist;

import android.app.Application;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class App extends Application {

    private static XposedService service;
    private static final ArrayList<XposedServiceHelper.OnServiceListener> listeners = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(@NonNull XposedService service) {
                App.service = service;
                for (var listener : listeners) {
                    listener.onServiceBind(service);
                }
            }

            @Override
            public void onServiceDied(@NonNull XposedService service) {
                App.service = service;
                for (var listener : listeners) {
                    listener.onServiceDied(service);
                }
            }
        });
    }

    public static void addServiceListener(XposedServiceHelper.OnServiceListener listener) {
        listeners.add(listener);
        if (service != null) listener.onServiceBind(service);
    }

    public static void removeServiceListener(XposedServiceHelper.OnServiceListener listener) {
        listeners.remove(listener);
    }
}
