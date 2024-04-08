package io.github.tehcneko.clipboardwhitelist;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class MainActivity extends Activity implements XposedServiceHelper.OnServiceListener {
    private final ExecutorService threads = Executors.newSingleThreadExecutor();
    private final AppAdapter adapter = new AppAdapter();

    private ListView appsList;
    private ProgressBar loading;
    private Set<String> whitelist;

    private XposedService service;

    private boolean showSystemApps = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        appsList = findViewById(R.id.apps);
        loading = findViewById(R.id.loading);

        appsList.setAdapter(adapter);

        appsList.setOnItemClickListener((parent, view, position, id) -> {
            var status = adapter.invertSelected(position);

            applyChange(((AppAdapter.App) adapter.getItem(position)).getPackageName(), status);
        });

        appsList.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        appsList.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets windowInsets) {
                Insets insets;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets = windowInsets.getInsets(WindowInsets.Type.systemBars());
                } else {
                    insets = windowInsets.getSystemWindowInsets();
                }
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return WindowInsets.CONSUMED;
                } else {
                    return windowInsets.consumeSystemWindowInsets();
                }
            }
        });

        updateLoading(false);
        App.addServiceListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        threads.shutdownNow();
        App.removeServiceListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        var searchItem = menu.findItem(R.id.search);
        var searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    adapter.updateSearchKeyword(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    adapter.updateSearchKeyword(newText);
                    return true;
                }
            });
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (item.getItemId() == R.id.systemApps) {
            item.setChecked(!item.isChecked());

            showSystemApps = item.isChecked();

            updateLoading(false);
            loadApps();

            return true;
        }

        return super.onMenuItemSelected(featureId, item);
    }

    private void loadApps() {
        threads.submit(() -> {
            try {
                var pm = getPackageManager();
                var apps = pm.getInstalledApplications(0).stream()
                        .filter((info) -> showSystemApps || (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
                        .map((info) -> AppAdapter.App.fromApplicationInfo(info, pm, whitelist.contains(info.packageName)))
                        .sorted()
                        .collect(Collectors.toList());

                runOnUiThread(() -> adapter.updateApps(apps));
                runOnUiThread(this::showNotice);
            } catch (Exception e) {
                showError(e);
            } finally {
                updateLoading(true);
            }
        });

    }

    private void updateLoading(boolean loaded) {
        runOnUiThread(() -> {
            if (loaded) {
                appsList.setVisibility(View.VISIBLE);
                loading.setVisibility(View.INVISIBLE);
            } else {
                appsList.setVisibility(View.INVISIBLE);
                loading.setVisibility(View.VISIBLE);
            }
        });
    }

    private void applyChange(String packageName, boolean exempted) {
        threads.submit(() -> {
            try {
                var preference = service.getRemotePreferences("clipboad_whitelist");

                if (exempted) {
                    whitelist.add(packageName);
                } else {
                    whitelist.remove(packageName);
                }
                preference.edit().putStringSet("whitelist", whitelist).apply();
            } catch (Exception e) {
                showError(e);
            }
        });
    }

    private void showError(Exception e) {
        final int title;
        final int content;

        if (e instanceof NullPointerException || e instanceof XposedService.ServiceException) {
            title = R.string.service_not_found;
            content = R.string.service_not_found_description;
        } else {
            title = R.string.unknown_error;
            content = R.string.unknown_error_description;

            Log.w("Load apps failed: " + e, e);
        }

        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(content)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .show()
        );
    }

    private void showNotice() {
        if (getSharedPreferences("ui", MODE_PRIVATE).getBoolean("skip_notice", false))
            return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.notice)
                .setMessage(R.string.application_clipboard_detect)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                })
                .show();

        getSharedPreferences("ui", MODE_PRIVATE).edit()
                .putBoolean("skip_notice", true)
                .apply();
    }

    @Override
    public void onServiceBind(@NonNull XposedService service) {
        this.service = service;
        var preference = service.getRemotePreferences("clipboad_whitelist");
        whitelist = new HashSet<>(preference.getStringSet("whitelist", Set.of()));
        loadApps();
    }

    @Override
    public void onServiceDied(@NonNull XposedService service) {
        this.service = null;
    }

    private class AppAdapter extends BaseAdapter {

        private List<App> apps;
        private List<App> appsShow;
        private String keyword = "";

        public AppAdapter() {
            this.appsShow = Collections.emptyList();
        }

        @Override
        public int getCount() {
            return appsShow.size();
        }

        @Override
        public Object getItem(int position) {
            return appsShow.get(position);
        }

        @Override
        public long getItemId(int position) {
            return appsShow.get(position).getPackageName().hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.app_adapter, parent, false);
            }

            final View icon = convertView.findViewById(R.id.icon);
            final TextView label = convertView.findViewById(R.id.label);
            final TextView packageName = convertView.findViewById(R.id.packageName);
            final CheckBox selected = convertView.findViewById(R.id.checkbox);

            final App app = appsShow.get(position);

            icon.setBackground(app.getIcon());
            label.setText(app.getLabel());
            packageName.setText(app.getPackageName());
            selected.setChecked(app.isSelected());

            return convertView;
        }

        public void updateApps(List<App> apps) {
            this.apps = apps;

            updateSearchKeyword(keyword);
        }

        public void updateSearchKeyword(String keyword) {
            this.keyword = keyword.toLowerCase();
            this.appsShow = apps.stream()
                    .filter(app -> keyword.isEmpty() || app.getLabel().toLowerCase().contains(keyword) || app.getPackageName().toLowerCase().contains(keyword))
                    .collect(Collectors.toList());

            this.notifyDataSetChanged();
        }

        public boolean invertSelected(int index) {
            final App app = appsShow.get(index);

            final boolean selected = !app.isSelected();

            app.setSelected(selected);

            this.notifyDataSetChanged();

            return selected;
        }

        public static final class App implements Comparable<App> {
            private final Drawable icon;
            private final String packageName;
            private final String label;

            private boolean selected;

            public App(Drawable icon, String packageName, String label, boolean selected) {
                this.icon = icon;
                this.packageName = packageName;
                this.label = label;
                this.selected = selected;
            }

            public static App fromApplicationInfo(ApplicationInfo info, PackageManager packageManager, boolean selected) {
                return new App(info.loadIcon(packageManager), info.packageName, info.loadLabel(packageManager).toString(), selected);
            }

            public Drawable getIcon() {
                return icon;
            }

            public String getPackageName() {
                return packageName;
            }

            public String getLabel() {
                return label;
            }

            public boolean isSelected() {
                return selected;
            }

            public void setSelected(boolean selected) {
                this.selected = selected;
            }

            @Override
            public int compareTo(App o) {
                if (selected && o.selected)
                    return label.compareTo(o.label);
                if (selected)
                    return -1;

                if (o.selected)
                    return 1;

                return label.compareTo(o.label);
            }
        }
    }
}
