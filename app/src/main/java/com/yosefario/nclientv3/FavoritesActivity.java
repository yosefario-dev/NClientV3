package com.yosefario.nclientv3;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.yosefario.nclientv3.components.activities.GeneralActivity;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.ui.favorites.FavoritesPagerAdapter;

public class FavoritesActivity extends GeneralActivity {
    public static final String EXTRA_OPEN_ONLINE = ".OPEN_ONLINE_FAVORITES";
    private static final String STATE_TAB = "selected_tab";

    private ViewPager2 viewPager;
    private FavoritesPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setTitle(R.string.favorite_screen);

        viewPager = findViewById(R.id.view_pager);
        pagerAdapter = new FavoritesPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        TabLayout tabs = findViewById(R.id.tabs);
        new TabLayoutMediator(tabs, viewPager, true,
            (tab, position) -> tab.setText(pagerAdapter.getPageTitle(position))).attach();

        int initialTab;
        if (savedInstanceState != null) {
            initialTab = savedInstanceState.getInt(STATE_TAB, Global.isDefaultFavoriteTabOnline() ? 1 : 0);
        } else if (getIntent().getBooleanExtra(getPackageName() + EXTRA_OPEN_ONLINE, false)) {
            initialTab = 1;
        } else {
            initialTab = Global.isDefaultFavoriteTabOnline() ? 1 : 0;
        }
        viewPager.setCurrentItem(initialTab, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTabTitles();
    }

    public void refreshTabTitles() {
        TabLayout tabs = findViewById(R.id.tabs);
        if (tabs == null || pagerAdapter == null) return;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            TabLayout.Tab tab = tabs.getTabAt(i);
            if (tab != null) tab.setText(pagerAdapter.getPageTitle(i));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, viewPager.getCurrentItem());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    public Fragment getPositionFragment(int position) {
        return getSupportFragmentManager().findFragmentByTag("f" + position);
    }

    @Nullable
    public Fragment getActualFragment() {
        return getPositionFragment(viewPager.getCurrentItem());
    }

    public void setOnlineCount(int count) {
        if (pagerAdapter != null) pagerAdapter.setOnlineCount(count);
        refreshTabTitles();
    }
}
