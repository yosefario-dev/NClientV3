package com.yosefario.nclientv3.components.views;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.JsonWriter;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import com.yosefario.nclientv3.CopyToClipboardActivity;
import com.yosefario.nclientv3.PINActivity;
import com.yosefario.nclientv3.R;
import com.yosefario.nclientv3.SettingsActivity;
import com.yosefario.nclientv3.StatusManagerActivity;
import com.yosefario.nclientv3.api.source.SourceId;
import com.yosefario.nclientv3.async.MetadataFetcher;
import com.yosefario.nclientv3.async.VersionChecker;
import com.yosefario.nclientv3.components.LocaleManager;
import com.yosefario.nclientv3.components.launcher.LauncherCalculator;
import com.yosefario.nclientv3.components.launcher.LauncherClock;
import com.yosefario.nclientv3.components.launcher.LauncherNotes;
import com.yosefario.nclientv3.components.launcher.LauncherReal;
import com.yosefario.nclientv3.settings.DefaultDialogs;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.settings.Login;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GeneralPreferenceFragment extends PreferenceFragmentCompat {
    private SettingsActivity act;

    public void setAct(SettingsActivity act) {
        this.act = act;
    }

    public void setType(SettingsActivity.Type type) {
        switch (type) {
            case MAIN:
                mainMenu();
                break;
            case COLUMN:
                columnMenu();
                break;
            case DATA:
                dataMenu();
                break;
        }
    }

    private void dataMenu() {
        addPreferencesFromResource(R.xml.settings_data);
        SeekBarPreference mobile = findPreference(getString(R.string.key_mobile_usage));
        SeekBarPreference wifi = findPreference(getString(R.string.key_wifi_usage));
        mobile.setOnPreferenceChangeListener((preference, newValue) -> {
            mobile.setTitle(getDataUsageString((Integer) newValue));
            return true;
        });
        wifi.setOnPreferenceChangeListener((preference, newValue) -> {
            wifi.setTitle(getDataUsageString((Integer) newValue));
            return true;
        });
        mobile.setTitle(getDataUsageString(mobile.getValue()));
        wifi.setTitle(getDataUsageString(wifi.getValue()));
        mobile.setUpdatesContinuously(true);
        wifi.setUpdatesContinuously(true);
    }

    private int getDataUsageString(int val) {
        switch (val) {
            case 0:
                return R.string.data_usage_no;
            case 1:
                return R.string.data_usage_thumb;
            case 2:
                return R.string.data_usage_full;
        }
        return R.string.data_usage_full;
    }

    private void fillRoba() {
        ArrayList<Pair<String, String>> languages = new ArrayList<>(LocaleManager.LANGUAGES.length);
        Locale actualLocale = Global.getLanguage(act);
        for (Locale l : LocaleManager.LANGUAGES) {
            languages.add(new Pair<>(l.toString(), l.getDisplayName(actualLocale)));
        }
        Collections.sort(languages, (o1, o2) -> o1.second.compareTo(o2.second));
        languages.add(0, new Pair<>(getString(R.string.key_default_value), getString(R.string.system_default)));
        ListPreference preference = findPreference(getString(R.string.key_language));
        assert preference != null;

        String[] languagesEntry = new String[languages.size()];
        String[] languagesNames = new String[languages.size()];
        for (int i = 0; i < languages.size(); i++) {
            Pair<String, String> lang = languages.get(i);
            languagesEntry[i] = lang.first;
            languagesNames[i] = Character.toUpperCase(lang.second.charAt(0)) + lang.second.substring(1);
        }

        preference.setEntryValues(languagesEntry);
        preference.setEntries(languagesNames);

    }

    private void mainMenu() {
        addPreferencesFromResource(R.xml.settings);

        fillRoba();

        findPreference("status_screen").setOnPreferenceClickListener(preference -> {
            Intent i = new Intent(act, StatusManagerActivity.class);
            act.runOnUiThread(() -> act.startActivity(i));
            return false;
        });
        Preference openLinks = findPreference("open_links");
        openLinks.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
        openLinks.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                Uri.parse("package:" + act.getPackageName())));
            return true;
        });
        findPreference("col_screen").setOnPreferenceClickListener(preference -> {
            Intent i = new Intent(act, SettingsActivity.class);
            i.putExtra(act.getPackageName() + ".TYPE", SettingsActivity.Type.COLUMN.ordinal());
            act.runOnUiThread(() -> act.startActivity(i));
            return false;
        });
        findPreference("data_screen").setOnPreferenceClickListener(preference -> {
            Intent i = new Intent(act, SettingsActivity.class);
            i.putExtra(act.getPackageName() + ".TYPE", SettingsActivity.Type.DATA.ordinal());
            act.runOnUiThread(() -> act.startActivity(i));
            return false;
        });
        findPreference("fetch_metadata").setVisible(Global.hasStoragePermission(act));
        findPreference("fetch_metadata").setOnPreferenceClickListener(preference -> {
            new Thread(new MetadataFetcher(act)).start();
            return true;
        });
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            findPreference(getString(R.string.key_use_rtl)).setVisible(false);
        }
        ListPreference disguisePref = findPreference(getString(R.string.key_disguise));
        if (disguisePref != null) {
            disguisePref.setValue(currentDisguise(act.getPackageManager()));
            disguisePref.setOnPreferenceChangeListener((preference, newValue) -> {
                applyDisguise(act.getPackageManager(), (String) newValue);
                return true;
            });
        }
        findPreference(getString(R.string.key_hide_multitasking)).setOnPreferenceChangeListener((preference, newValue) -> {
            Global.setHideMultitask(Boolean.TRUE.equals(newValue));
            Toast.makeText(act, R.string.restarting_to_apply, Toast.LENGTH_SHORT).show();
            new Handler(Looper.getMainLooper()).postDelayed(() -> Global.restartApp(act), 600);
            return true;
        });
        findPreference(getString(R.string.key_use_account_tag)).setEnabled(Login.isLogged());

        findPreference(getString(R.string.key_theme_select)).setOnPreferenceChangeListener((preference, newValue) -> {
            act.recreate();
            return true;
        });
        findPreference(getString(R.string.key_language)).setOnPreferenceChangeListener((preference, newValue) -> {
            act.recreate();
            return true;
        });
        findPreference(getString(R.string.key_enable_beta)).setOnPreferenceChangeListener((preference, newValue) -> {
            //Instant update to allow search for updates
            Global.setEnableBeta((Boolean) newValue);
            return true;
        });
        Preference sendUaPref = findPreference(getString(R.string.key_send_app_user_agent));
        if (sendUaPref != null) {
            sendUaPref.setOnPreferenceChangeListener((preference, newValue) -> {
                Global.setSendAppUserAgent(act, Boolean.TRUE.equals(newValue));
                return true;
            });
        }
        findPreference("has_pin").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue.equals(Boolean.TRUE)) {
                Intent i = new Intent(act, PINActivity.class);
                i.putExtra(act.getPackageName() + ".SET", true);
                startActivity(i);
                act.finish();
                return false;
            }
            act.getSharedPreferences("Settings", 0).edit().remove("pin").apply();
            return true;
        });

        findPreference("version").setTitle(getString(R.string.app_version_format, Global.getVersionName(getContext())));
        initStoragePaths(findPreference(getString(R.string.key_save_path)));
        double cacheSize = Global.recursiveSize(act.getCacheDir()) / ((double) (1 << 20));
        findPreference(getString(R.string.key_save_path)).setOnPreferenceChangeListener((preference, newValue) -> {
            if (!newValue.equals(getString(R.string.custom_path))) return true;
            manageCustomPath();
            return false;
        });
        //clear cache if pressed
        findPreference(getString(R.string.key_cache)).setSummary(getString(R.string.cache_size_formatted, cacheSize));
        findPreference(getString(R.string.key_cookie)).setOnPreferenceClickListener(preference -> {
            Login.clearCookies();
            CookieManager.getInstance().removeAllCookie();
            return true;
        });
        findPreference(getString(R.string.key_cache)).setOnPreferenceClickListener(preference -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(act);
            builder.setTitle(R.string.clear_cache);
            builder.setPositiveButton(R.string.yes, (dialog, which) -> {
                Global.recursiveDelete(act.getCacheDir());
                act.runOnUiThread(() -> {
                    Toast.makeText(act, act.getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show();
                    double cSize = Global.recursiveSize(act.getCacheDir()) / ((double) (2 << 20));
                    findPreference(getString(R.string.key_cache)).setSummary(getString(R.string.cache_size_formatted, cSize));
                });

            }).setNegativeButton(R.string.no, null).setCancelable(true);
            builder.show();

            return true;
        });
        findPreference(getString(R.string.key_update)).setOnPreferenceClickListener(preference -> {
            new VersionChecker(act, false);
            return true;
        });
        findPreference("bug").setOnPreferenceClickListener(preference -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yosefario-dev/NClientV3/issues/new"));
            startActivity(i);
            return true;
        });
        findPreference("donate").setOnPreferenceClickListener(preference -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yosefario-dev/NClientV3#donation"));
            startActivity(i);
            return true;
        });
        findPreference("copy_settings").setOnPreferenceClickListener(preference -> {
            try {
                CopyToClipboardActivity.copyTextToClipboard(getContext(), getDataSettings(getContext()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        });
        findPreference("export").setOnPreferenceClickListener(preference -> {
            act.exportSettings();
            return true;
        });
        findPreference("import").setOnPreferenceClickListener(preference -> {
            act.importSettings();
            return true;
        });

        Preference mirror = findPreference(getString(R.string.key_site_mirror));
        String currentMirror = act.getSharedPreferences("Settings", Context.MODE_PRIVATE)
            .getString(getString(R.string.key_site_mirror), "");
        mirror.setSummary(sourceSummary());
        mirror.setOnPreferenceClickListener(preference -> {
            showSourcePicker(preference);
            return true;
        });

        updateSourceDependentVisibility(Global.isSourceConfigured());

        if (act.getIntent() != null
            && act.getIntent().getBooleanExtra(SettingsActivity.EXTRA_OPEN_SOURCE_PICKER, false)) {
            act.getIntent().removeExtra(SettingsActivity.EXTRA_OPEN_SOURCE_PICKER);
            new Handler(Looper.getMainLooper()).post(() -> showSourcePicker(mirror));
        }
    }

    private void showSourcePicker(Preference preference) {
        DefaultDialogs.showSourceSelector(requireContext(), () -> {
            preference.setSummary(sourceSummary());
            updateSourceDependentVisibility(Global.isSourceConfigured());
        });
    }

    private String sourceSummary() {
        if (!Global.isSourceConfigured())
            return getString(R.string.source_not_configured);
        StringBuilder sb = new StringBuilder();
        for (SourceId id : Global.getEnabledSources()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(id.displayName());
        }
        return sb.toString();
    }

    private void updateSourceDependentVisibility(boolean visible) {
        // Hide/show entire preference categories that depend on a configured source
        PreferenceCategory navCategory = findPreference("category_navigation");
        PreferenceCategory imgCategory = findPreference("category_images");
        PreferenceCategory searchCategory = findPreference("category_search");
        if (navCategory != null) navCategory.setVisible(visible);
        if (imgCategory != null) imgCategory.setVisible(visible);
        if (searchCategory != null) searchCategory.setVisible(visible);

        // Hide individual source-dependent prefs in Global settings
        Preference userAgent = findPreference(getString(R.string.key_user_agent));
        Preference clearCookies = findPreference(getString(R.string.key_cookie));
        Preference titleType = findPreference(getString(R.string.key_title_type));
        if (userAgent != null) userAgent.setVisible(visible);
        if (clearCookies != null) clearCookies.setVisible(visible);
        if (titleType != null) titleType.setVisible(visible);
    }

    public void manageCustomPath() {
        if (!Global.isExternalStorageManager()) {
            act.requestStorageManager();
            return;
        }
        final String key = getString(R.string.key_save_path);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(act);
        AppCompatAutoCompleteTextView edit = (AppCompatAutoCompleteTextView) View.inflate(act, R.layout.autocomplete_entry, null);
        edit.setHint(R.string.insert_path);
        builder.setView(edit);
        builder.setTitle(R.string.insert_path);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            act.getSharedPreferences("Settings", Context.MODE_PRIVATE).edit().putString(key, edit.getText().toString()).apply();
            findPreference(key).setSummary(edit.getText().toString());
        }).setNegativeButton(R.string.cancel, null).show();
    }

    private void changeLauncher(PackageManager pm, ComponentName name, boolean enabled) {
        int enableState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(name, enableState, PackageManager.DONT_KILL_APP);
    }

    private ComponentName disguiseComponent(String value) {
        Class<?> alias;
        switch (value == null ? "none" : value) {
            case "calculator": alias = LauncherCalculator.class; break;
            case "clock": alias = LauncherClock.class; break;
            case "notes": alias = LauncherNotes.class; break;
            default: alias = LauncherReal.class; break;
        }
        return new ComponentName(act, alias);
    }

    private void applyDisguise(PackageManager pm, String value) {
        ComponentName selected = disguiseComponent(value);
        changeLauncher(pm, selected, true);
        for (String other : new String[]{"none", "calculator", "clock", "notes"}) {
            ComponentName cn = disguiseComponent(other);
            if (!cn.equals(selected)) changeLauncher(pm, cn, false);
        }
    }

    private String currentDisguise(PackageManager pm) {
        for (String value : new String[]{"calculator", "clock", "notes"}) {
            if (pm.getComponentEnabledSetting(disguiseComponent(value))
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return value;
            }
        }
        return "none";
    }


    private void initStoragePaths(ListPreference storagePreference) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || !Global.hasStoragePermission(act)) {
            storagePreference.setVisible(false);
            return;
        }
        List<File> files = Global.getUsableFolders(act);
        List<CharSequence> strings = new ArrayList<>(files.size() + 1);
        for (File f : files) {
            if (f != null)
                strings.add(f.getAbsolutePath());
        }
        strings.add(getString(R.string.custom_path));
        storagePreference.setEntries(strings.toArray(new CharSequence[0]));
        storagePreference.setEntryValues(strings.toArray(new CharSequence[0]));
        storagePreference.setSummary(
            act.getSharedPreferences("Settings", Context.MODE_PRIVATE)
                .getString(getString(R.string.key_save_path), Global.MAINFOLDER.getParent())
        );
        storagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary(newValue.toString());
            return true;
        });
    }

    private String getDataSettings(Context context) throws IOException {
        String[] names = new String[]{"Settings", "ScrapedTags"};
        StringWriter sw = new StringWriter();
        JsonWriter writer = new JsonWriter(sw);
        writer.setIndent("\t");

        writer.beginObject();
        for (String name : names)
            processSharedFromName(writer, context, name);
        writer.endObject();

        writer.flush();
        String settings = sw.toString();
        writer.close();

        LogUtility.d(settings);
        return settings;
    }

    private void processSharedFromName(JsonWriter writer, Context context, String name) throws IOException {
        writer.name(name);
        writer.beginObject();
        SharedPreferences preferences = context.getSharedPreferences(name, 0);
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            writeEntry(writer, entry);
        }
        writer.endObject();
    }

    private void writeEntry(JsonWriter writer, Map.Entry<String, ?> entry) throws IOException {
        writer.name(entry.getKey());
        if (entry.getValue() instanceof Integer) writer.value((Integer) entry.getValue());
        else if (entry.getValue() instanceof Boolean) writer.value((Boolean) entry.getValue());
        else if (entry.getValue() instanceof String) writer.value((String) entry.getValue());
        else if (entry.getValue() instanceof Long) writer.value((Long) entry.getValue());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName("Settings");
    }

    private void columnMenu() {
        addPreferencesFromResource(R.xml.settings_column);
    }

}
