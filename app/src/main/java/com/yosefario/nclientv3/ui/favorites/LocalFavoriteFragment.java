package com.yosefario.nclientv3.ui.favorites;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yosefario.nclientv3.FavoriteActivity;
import com.yosefario.nclientv3.FavoritesActivity;
import com.yosefario.nclientv3.R;
import com.yosefario.nclientv3.adapters.FavoriteAdapter;
import com.yosefario.nclientv3.api.components.Gallery;
import com.yosefario.nclientv3.async.database.Queries;
import com.yosefario.nclientv3.async.downloader.DownloadGalleryV2;
import com.yosefario.nclientv3.components.views.PageSwitcher;
import com.yosefario.nclientv3.components.widgets.CustomGridLayoutManager;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.utility.Utility;

public class LocalFavoriteFragment extends Fragment implements FavoriteAdapter.Host {
    private RecyclerView recycler;
    private SwipeRefreshLayout refresher;
    private PageSwitcher pageSwitcher;
    private View emptyState;
    private FavoriteAdapter adapter;
    private SearchView searchView;
    private boolean sortByTitle = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite_local, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);
        recycler = view.findViewById(R.id.recycler);
        refresher = view.findViewById(R.id.refresher);
        pageSwitcher = view.findViewById(R.id.page_switcher);
        emptyState = view.findViewById(R.id.empty_state_container);

        refresher.setRefreshing(true);
        adapter = new FavoriteAdapter(requireActivity(), this);
        refresher.setOnRefreshListener(adapter::forceReload);
        changeLayout(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        recycler.setAdapter(adapter);

        pageSwitcher.setPages(1, 1);
        pageSwitcher.setChanger(new PageSwitcher.DefaultPageChanger() {
            @Override
            public void pageChanged(PageSwitcher switcher, int page) {
                if (adapter != null) adapter.changePage();
            }
        });
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updateEmptyState();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                updateEmptyState();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                updateEmptyState();
            }
        });
    }

    private void changeLayout(boolean landscape) {
        CustomGridLayoutManager manager = (CustomGridLayoutManager) recycler.getLayoutManager();
        RecyclerView.Adapter<?> currentAdapter = recycler.getAdapter();
        int count = landscape ? Global.getColLandFavorite() : Global.getColPortFavorite();
        int position = 0;
        if (manager != null) position = manager.findFirstCompletelyVisibleItemPosition();
        CustomGridLayoutManager glm = new CustomGridLayoutManager(requireContext(), count);
        recycler.setLayoutManager(glm);
        recycler.setAdapter(currentAdapter);
        recycler.scrollToPosition(Math.max(0, position));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (recycler != null)
            changeLayout(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresher.setEnabled(true);
        refresher.setRefreshing(true);
        String query = searchView == null ? null : searchView.getQuery().toString();
        pageSwitcher.setTotalPage(calculatePages(query));
        adapter.forceReload();
    }

    private void updateEmptyState() {
        boolean empty = adapter.getItemCount() == 0;
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (getActivity() instanceof FavoritesActivity)
            ((FavoritesActivity) getActivity()).refreshTabTitles();
    }

    private int calculatePages(@Nullable String text) {
        int perPage = FavoriteActivity.getEntryPerPage();
        int totalEntries = Queries.FavoriteTable.countFavorite(text);
        int div = totalEntries / perPage;
        int mod = totalEntries % perPage;
        return div + (mod == 0 ? 0 : 1);
    }

    @Override
    public int getActualPage() {
        return pageSwitcher.getActualPage();
    }

    @Override
    public SwipeRefreshLayout getRefresher() {
        return refresher;
    }

    @Override
    public void runOnUiThread(Runnable runnable) {
        requireActivity().runOnUiThread(runnable);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
        menu.findItem(R.id.download_page).setVisible(true);
        menu.findItem(R.id.sort_by_name).setVisible(true);
        menu.findItem(R.id.by_popular).setVisible(false);
        menu.findItem(R.id.only_language).setVisible(false);
        menu.findItem(R.id.add_bookmark).setVisible(false);
        menu.findItem(R.id.random_favorite).setVisible(true);

        searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                pageSwitcher.setTotalPage(calculatePages(newText));
                if (adapter != null) adapter.getFilter().filter(newText);
                return true;
            }
        });
        Utility.tintMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.open_browser) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Utility.getBaseUrl() + "favorites/")));
            return true;
        } else if (item.getItemId() == R.id.download_page) {
            if (adapter != null) showDialogDownloadAll();
            return true;
        } else if (item.getItemId() == R.id.sort_by_name) {
            sortByTitle = !sortByTitle;
            adapter.setSortByTitle(sortByTitle);
            item.setTitle(sortByTitle ? R.string.sort_by_latest : R.string.sort_by_title);
            return true;
        } else if (item.getItemId() == R.id.random_favorite) {
            adapter.randomGallery();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDialogDownloadAll() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.download_all_galleries_in_this_page)
            .setIcon(R.drawable.ic_file)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                for (Gallery g : adapter.getAllGalleries())
                    DownloadGalleryV2.downloadGallery(requireContext(), g);
            })
            .show();
    }
}
