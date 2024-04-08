package io.github.tehcneko.clipboardwhitelist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AppAdapter extends BaseAdapter {
    private final Context context;

    private List<App> apps;
    private List<App> appsShow;
    private String keyword = "";

    public AppAdapter(Context context) {
        this.context = context;
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
            convertView = LayoutInflater.from(context).inflate(R.layout.app_adapter, parent, false);
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
}
