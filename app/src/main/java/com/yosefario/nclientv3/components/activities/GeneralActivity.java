package com.yosefario.nclientv3.components.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.yosefario.nclientv3.R;
import com.yosefario.nclientv3.components.views.CFTokenView;
import com.yosefario.nclientv3.settings.Global;

import java.lang.ref.WeakReference;

public abstract class GeneralActivity extends AppCompatActivity {
    private boolean isFastScrollerApplied = false;
    private static WeakReference<GeneralActivity> lastActivity;
    private CFTokenView tokenView = null;

    public static @Nullable
    CFTokenView getLastCFView() {
        if (lastActivity == null) return null;
        GeneralActivity activity = lastActivity.get();
        if (activity != null) {
            activity.runOnUiThread(activity::inflateWebView);
            return activity.tokenView;
        }
        return null;
    }

    private void inflateWebView() {
        if (tokenView == null) {
            Toast.makeText(this, R.string.fetching_cloudflare_token, Toast.LENGTH_SHORT).show();
            ViewGroup rootView= (ViewGroup) findViewById(android.R.id.content).getRootView();
            ViewGroup v= (ViewGroup) LayoutInflater.from(this).inflate(R.layout.cftoken_layout,rootView,false);
            tokenView = new CFTokenView(v);
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            tokenView.setVisibility(View.GONE);
            this.addContentView(v, params);
        }
    }

    @Override
    protected void onPause() {
        if (Global.hideMultitask())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onPause();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Global.initActivity(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        if (!applySystemBarInsets()) return;
        View content = findViewById(android.R.id.content);
        if (content == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Override and return false for activities that manage system bar insets themselves
     * (immersive readers, layouts with their own DrawerLayout / fitsSystemWindows chain).
     */
    protected boolean applySystemBarInsets() {
        return true;
    }

    @Override
    protected void onResume() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onResume();
        lastActivity = new WeakReference<>(this);
        if (!isFastScrollerApplied) {
            isFastScrollerApplied = true;
            Global.applyFastScroller(findViewById(R.id.recycler));
        }
    }
}
