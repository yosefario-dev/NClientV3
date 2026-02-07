package com.yosefario.nclientv3.components.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import com.yosefario.nclientv3.BuildConfig;
import com.yosefario.nclientv3.R;
import com.yosefario.nclientv3.api.local.LocalGallery;
import com.yosefario.nclientv3.async.ScrapeTags;
import com.yosefario.nclientv3.async.database.DatabaseHelper;
import com.yosefario.nclientv3.async.downloader.DownloadGalleryV2;
import com.yosefario.nclientv3.settings.Database;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.settings.TagV2;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.network.NetworkUtil;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CrashApplication extends Application {
    private static final String SIGNATURE_GITHUB = "ce96fdbcc89cc991f083320140c148db5f";

    @Override
    public void onCreate() {
        super.onCreate();
        // Material 3 dynamic color support (Android 12+)
        DynamicColors.applyToActivitiesIfAvailable(this);
        Global.initLanguage(this);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        Global.initStorage(this);
        Database.setDatabase(new DatabaseHelper(getApplicationContext()).getWritableDatabase());
        String version = Global.getLastVersion(this), actualVersion = Global.getVersionName(this);
        SharedPreferences preferences = getSharedPreferences("Settings", 0);
        if (!actualVersion.equals(version))
            afterUpdateChecks(preferences, version, actualVersion);

        Global.initFromShared(this);
        NetworkUtil.initConnectivity(this);
        TagV2.initMinCount(this);
        TagV2.initSortByName(this);
        DownloadGalleryV2.loadDownloads(this);
    }

    private boolean signatureCheck() {
        try {
            @SuppressLint("PackageManagerGetSignatures")
            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                getPackageName(), PackageManager.GET_SIGNATURES);
            //note sample just checks the first signature

            for (Signature signature : packageInfo.signatures) {
                // MD5 is used because it is not a secure data
                MessageDigest m = MessageDigest.getInstance("MD5");
                m.update(signature.toByteArray());
                String hash = new BigInteger(1, m.digest()).toString(16);
                LogUtility.d("Find signature: " + hash);
                if (SIGNATURE_GITHUB.equals(hash)) return true;
            }
        } catch (NullPointerException | PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void afterUpdateChecks(SharedPreferences preferences, String oldVersion, String actualVersion) {
        SharedPreferences.Editor editor = preferences.edit();
        removeOldUpdates();
        //update tags
        ScrapeTags.startWork(this);
        if ("0.0.0".equals(oldVersion))
            editor.putBoolean(getString(R.string.key_check_update), signatureCheck());
        editor.apply();
        Global.setLastVersion(this);
    }


    private void createIdHiddenFile(File folder) {
        LocalGallery gallery = new LocalGallery(folder);
        if (gallery.getId() < 0) return;
        File hiddenFile = new File(folder, "." + gallery.getId());
        try {
            hiddenFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createIdHiddenFiles() {
        if (!Global.hasStoragePermission(this)) return;
        File[] files = Global.DOWNLOADFOLDER.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory())
                createIdHiddenFile(f);
        }
    }

    private void removeOldUpdates() {
        if (!Global.hasStoragePermission(this)) return;
        Global.recursiveDelete(Global.UPDATEFOLDER);
        Global.UPDATEFOLDER.mkdir();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
    }
}
