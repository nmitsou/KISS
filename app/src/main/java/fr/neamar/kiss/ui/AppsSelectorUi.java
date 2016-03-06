package fr.neamar.kiss.ui;

import android.app.ListActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;

public class AppsSelectorUi extends ListActivity {

    private SharedPreferences prefs;
    private List<String> appNames;
    private boolean stateChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String theme = prefs.getString("theme", "light");
        if (theme.contains("dark")) {
            setTheme(R.style.SettingThemeDark);
        }
        setContentView(R.layout.activity_apps_selector_ui);


        ListView appsListView = getListView();
        appsListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        appNames = loadExcludedAppNames();

        if (appNames.size() == 1) {
            //No excluded app found. Don't show activity
            Toast.makeText(this, getString(R.string.no_excluded_apps), Toast.LENGTH_SHORT).show();
            finish();
        }

        TextView txtView = (TextView)findViewById(R.id.txtViewTitle);
        txtView.setText(R.string.edit_excluded_apps_name);

        setListAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_checked, appNames.subList(1, appNames.size())));
        checkSelectedApps(this, appsListView, appNames);
    }

    private void checkSelectedApps(Context context, ListView appsListView, List<String> appNames)
    {
        for (int i=0;i < appsListView.getAdapter().getCount();i++) {
            appsListView.setItemChecked(i, true);
        }
    }

    private List<String> loadExcludedAppNames() {

        String excludedAppList = prefs.
                getString("excluded-apps-list", this.getPackageName() + ";");
        List excludedApps = Arrays.asList(excludedAppList.split(";"));
        return excludedApps;
        //return excludedApps;
    }

    @Override
    public void onListItemClick(ListView parent, View v, int position, long id) {
        stateChanged = true;
        String excludedAppList = PreferenceManager.getDefaultSharedPreferences(this).
                getString("excluded-apps-list", this.getPackageName() + ";");
        if (((CheckedTextView)v).isChecked())
        {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString("excluded-apps-list", excludedAppList + appNames.get(position+1) + ";").commit();
            //Toast.makeText(this, R.string.excluded_app_list_added, Toast.LENGTH_LONG).show();
        }
        else {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString("excluded-apps-list", excludedAppList.replace(appNames.get(position+1) + ";", "")).commit();

        }
    }


    @Override
    public void onBackPressed() {
        if (stateChanged) {
            KissApplication.getDataHandler(this).getAppProvider().reload();
        }
        super.onBackPressed();
    }
}
