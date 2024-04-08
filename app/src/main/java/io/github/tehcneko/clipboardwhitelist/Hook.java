package io.github.tehcneko.clipboardwhitelist;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public class Hook extends XposedModule {
    public static Set<String> whitelist;

    public Hook(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
    }

    @Override
    public void onSystemServerLoaded(@NonNull SystemServerLoadedParam param) {
        var classLoader = param.getClassLoader();

        var preference = getRemotePreferences("clipboad_whitelist");
        whitelist = preference.getStringSet("whitelist", Set.of());
        preference.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            if ("whitelist".equals(key)) {
                whitelist = preference.getStringSet("whitelist", Set.of());
            }
        });
        try {
            hookIsDefaultIme(classLoader);
        } catch (Throwable t) {
            log("hook isDefaultIme failed", t);
        }
    }

    private void hookIsDefaultIme(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var clipboardServiceClazz = classLoader.loadClass("com.android.server.clipboard.ClipboardService");
        var isDefaultImeMethod = clipboardServiceClazz.getDeclaredMethod("isDefaultIme", int.class, String.class);
        hook(isDefaultImeMethod, IsDefaultIMEHooker.class);
    }

    @XposedHooker
    private static class IsDefaultIMEHooker implements Hooker {

        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            var packageName = (String) callback.getArgs()[1];

            if (whitelist.contains(packageName)) {
                callback.returnAndSkip(true);
            }
        }
    }
}
