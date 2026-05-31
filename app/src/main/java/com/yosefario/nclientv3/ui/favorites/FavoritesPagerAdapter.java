package com.yosefario.nclientv3.ui.favorites;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.yosefario.nclientv3.FavoritesActivity;
import com.yosefario.nclientv3.R;
import com.yosefario.nclientv3.async.database.Queries;

import java.util.Locale;

public class FavoritesPagerAdapter extends FragmentStateAdapter {
    private final FavoritesActivity activity;
    private int onlineCount = -1;

    public FavoritesPagerAdapter(FavoritesActivity activity) {
        super(activity.getSupportFragmentManager(), activity.getLifecycle());
        this.activity = activity;
    }

    public CharSequence getPageTitle(int position) {
        if (position == 0) {
            return formatCount(R.string.favorite_tab_local, Queries.FavoriteTable.countFavorite());
        }
        if (onlineCount < 0) return activity.getString(R.string.favorite_tab_online);
        return formatCount(R.string.favorite_tab_online, onlineCount);
    }

    public void setOnlineCount(int count) {
        this.onlineCount = count;
    }

    private CharSequence formatCount(int labelRes, int count) {
        return String.format(Locale.US, activity.getString(R.string.favorite_tab_count),
            activity.getString(labelRes), count);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return position == 0 ? new LocalFavoriteFragment() : new OnlineFavoriteFragment();
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
