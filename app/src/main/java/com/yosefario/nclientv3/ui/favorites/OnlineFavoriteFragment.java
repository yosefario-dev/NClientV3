package com.yosefario.nclientv3.ui.favorites;

import android.content.Intent;
import android.content.res.Configuration;
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
import com.google.android.material.snackbar.Snackbar;
import com.yosefario.nclientv3.FavoritesActivity;
import com.yosefario.nclientv3.GalleryActivity;
import com.yosefario.nclientv3.LoginActivity;
import com.yosefario.nclientv3.R;
import com.yosefario.nclientv3.adapters.ListAdapter;
import com.yosefario.nclientv3.api.InspectorV3;
import com.yosefario.nclientv3.api.components.GenericGallery;
import com.yosefario.nclientv3.async.downloader.DownloadGalleryV2;
import com.yosefario.nclientv3.components.activities.GeneralActivity;
import com.yosefario.nclientv3.components.views.PageSwitcher;
import com.yosefario.nclientv3.components.widgets.CustomGridLayoutManager;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.settings.Login;
import com.yosefario.nclientv3.utility.Utility;

import java.util.List;

public class OnlineFavoriteFragment extends Fragment {
    private RecyclerView recycler;
    private SwipeRefreshLayout refresher;
    private PageSwitcher pageSwitcher;
    private View loginCta;
    private View emptyState;

    private ListAdapter adapter;
    private InspectorV3 inspector = null;
    private boolean inspecting = false;
    private boolean wasLogged = false;

    private static final int DEFAULT_FAV_PER_PAGE = 25;
    private String currentQuery = null;
    private int perPage = DEFAULT_FAV_PER_PAGE;
    private int onlineTotal = -1;
    private String totalKey = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite_online, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);
        recycler = view.findViewById(R.id.recycler);
        refresher = view.findViewById(R.id.refresher);
        pageSwitcher = view.findViewById(R.id.page_switcher);
        loginCta = view.findViewById(R.id.login_cta_container);
        emptyState = view.findViewById(R.id.empty_state_container);

        view.findViewById(R.id.login_cta_button).setOnClickListener(v ->
            startActivity(new Intent(requireActivity(), LoginActivity.class)));

        adapter = new ListAdapter(activity());
        recycler.setAdapter(adapter);
        recycler.setHasFixedSize(true);
        changeLayout(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (inspecting || inspector == null) return;
                if (!Global.isInfiniteScrollMain()) return;
                if (refresher.isRefreshing()) return;
                CustomGridLayoutManager manager = (CustomGridLayoutManager) recycler.getLayoutManager();
                if (manager == null) return;
                if (!pageSwitcher.lastPageReached() && lastGalleryReached(manager)) {
                    inspecting = true;
                    inspector = inspector.cloneInspector(activity(), addDataset);
                    inspector.setPage(inspector.getPage() + 1);
                    inspector.start();
                }
            }
        });

        refresher.setOnRefreshListener(() -> {
            if (inspector == null) return;
            totalKey = null;
            inspector = inspector.cloneInspector(activity(), resetDataset);
            if (Global.isInfiniteScrollMain()) inspector.setPage(1);
            inspector.start();
        });

        pageSwitcher.setChanger(new PageSwitcher.DefaultPageChanger() {
            @Override
            public void pageChanged(PageSwitcher switcher, int page) {
                if (inspector == null) return;
                inspector = inspector.cloneInspector(activity(), resetDataset);
                inspector.setPage(pageSwitcher.getActualPage());
                inspector.start();
            }
        });

        if (Login.isLogged()) {
            wasLogged = true;
            startFavoriteInspector(1);
        } else {
            showLoginCta();
        }
    }

    private GeneralActivity activity() {
        return (GeneralActivity) requireActivity();
    }

    @Override
    public void onResume() {
        super.onResume();
        Login.initLogin(requireActivity());
        boolean logged = Login.isLogged();
        if (logged && !wasLogged) {
            wasLogged = true;
            hideLoginCta();
            startFavoriteInspector(1);
        } else if (!logged) {
            wasLogged = false;
            showLoginCta();
        } else if (adapter != null) {
            adapter.resetStatuses();
            adapter.notifyDataSetChanged();
        }
    }

    private void startFavoriteInspector(int page) {
        hideLoginCta();
        currentQuery = null;
        totalKey = null;
        inspector = InspectorV3.favoriteInspector(activity(), null, page, resetDataset);
        inspector.start();
    }

    private void changeLayout(boolean landscape) {
        RecyclerView.LayoutManager manager = recycler.getLayoutManager();
        int count = landscape ? Global.getColLandMain() : Global.getColPortMain();
        int position = 0;
        if (manager instanceof CustomGridLayoutManager)
            position = ((CustomGridLayoutManager) manager).findFirstCompletelyVisibleItemPosition();
        recycler.setLayoutManager(new CustomGridLayoutManager(requireContext(), count));
        recycler.scrollToPosition(Math.max(0, position));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (recycler != null)
            changeLayout(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    private boolean lastGalleryReached(CustomGridLayoutManager manager) {
        return manager.findLastVisibleItemPosition() >= (recycler.getAdapter().getItemCount() - 1 - manager.getSpanCount());
    }

    private void showPageSwitcher(int actualPage, int totalPage) {
        pageSwitcher.setPages(totalPage, actualPage);
        if (Global.isInfiniteScrollMain()) pageSwitcher.setVisibility(View.GONE);
    }

    private void runOnUi(Runnable runnable) {
        if (getActivity() != null) requireActivity().runOnUiThread(runnable);
    }

    private void showLoginCta() {
        loginCta.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        refresher.setVisibility(View.GONE);
        pageSwitcher.setVisibility(View.GONE);
    }

    private void hideLoginCta() {
        loginCta.setVisibility(View.GONE);
        refresher.setVisibility(View.VISIBLE);
    }

    private void showEmptyState(boolean empty) {
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void pushOnlineCount(int count) {
        if (getActivity() instanceof FavoritesActivity)
            ((FavoritesActivity) getActivity()).setOnlineCount(count);
    }

    private abstract class FavoriteInspectorResponse extends InspectorV3.DefaultInspectorResponse {
        @Override
        public void onStart() {
            runOnUi(() -> {
                refresher.setRefreshing(true);
                showEmptyState(false);
            });
        }

        @Override
        public void onSuccess(List<GenericGallery> galleries) {
            if (adapter != null) adapter.resetStatuses();
            runOnUi(() -> showEmptyState(galleries.isEmpty()));
        }

        @Override
        public void onEnd() {
            runOnUi(() -> refresher.setRefreshing(false));
            inspecting = false;
        }

        @Override
        public void onFailure(Exception e) {
            super.onFailure(e);
            runOnUi(() -> {
                refresher.setRefreshing(false);
                View root = getView();
                if (root == null || inspector == null) return;
                Snackbar.make(root, R.string.unable_to_connect_to_the_site, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.retry, v -> {
                        inspector = inspector.cloneInspector(activity(), inspector.getResponse());
                        inspector.start();
                    }).show();
            });
        }

        @Override
        public boolean shouldStart(InspectorV3 inspector) {
            return true;
        }
    }

    private final InspectorV3.InspectorResponse resetDataset = new FavoriteInspectorResponse() {
        @Override
        public void onSuccess(List<GenericGallery> galleries) {
            super.onSuccess(galleries);
            adapter.restartDataset(galleries);
            runOnUi(() -> {
                showPageSwitcher(inspector.getPage(), inspector.getPageCount());
                recycler.smoothScrollToPosition(0);
            });
            updateOnlineTotal();
        }
    };

    private final InspectorV3.InspectorResponse addDataset = new FavoriteInspectorResponse() {
        @Override
        public void onSuccess(List<GenericGallery> galleries) {
            adapter.addGalleries(galleries);
        }
    };

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
        menu.findItem(R.id.by_popular).setVisible(false);
        menu.findItem(R.id.only_language).setVisible(false);
        menu.findItem(R.id.add_bookmark).setVisible(false);
        menu.findItem(R.id.sort_by_name).setVisible(false);
        boolean logged = Login.isLogged();
        menu.findItem(R.id.random_favorite).setVisible(logged);
        menu.findItem(R.id.download_page).setVisible(logged);

        MenuItem searchItem = menu.findItem(R.id.search);
        searchItem.setVisible(logged);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchFavorites(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        Utility.tintMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.open_browser) {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Utility.getBaseUrl() + "favorites/")));
            return true;
        } else if (item.getItemId() == R.id.random_favorite) {
            openRandomFavorite();
            return true;
        } else if (item.getItemId() == R.id.download_page) {
            if (inspector != null && inspector.getGalleries() != null) showDialogDownloadAll();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void searchFavorites(String query) {
        currentQuery = query;
        totalKey = null;
        inspector = InspectorV3.favoriteInspector(activity(), query, 1, resetDataset);
        inspector.start();
    }

    private void updateOnlineTotal() {
        InspectorV3 current = inspector;
        if (current == null) return;
        int pageCount = current.getPageCount();
        List<GenericGallery> page = current.getGalleries();
        int loaded = page == null ? 0 : page.size();
        if (current.getPage() == 1 && loaded > 0) perPage = loaded;
        String key = currentQuery + "|" + pageCount;
        if (key.equals(totalKey) && onlineTotal >= 0) {
            pushTotal(onlineTotal);
            return;
        }
        if (pageCount <= 1) {
            commitTotal(key, loaded);
            return;
        }
        if (current.getPage() == pageCount) {
            commitTotal(key, perPage * (pageCount - 1) + loaded);
            return;
        }
        InspectorV3.favoriteInspector(activity(), currentQuery, pageCount,
            new InspectorV3.DefaultInspectorResponse() {
                @Override
                public void onSuccess(List<GenericGallery> galleries) {
                    commitTotal(key, perPage * (pageCount - 1) + galleries.size());
                }
            }).start();
    }

    private void commitTotal(String key, int total) {
        totalKey = key;
        onlineTotal = total;
        pushTotal(total);
    }

    private void pushTotal(int total) {
        runOnUi(() -> pushOnlineCount(total));
    }

    private void openRandomFavorite() {
        InspectorV3.randomInspector(activity(), new InspectorV3.DefaultInspectorResponse() {
            @Override
            public void onSuccess(List<GenericGallery> galleries) {
                if (galleries.isEmpty() || getActivity() == null) return;
                Intent intent = new Intent(requireActivity(), GalleryActivity.class);
                intent.putExtra(requireActivity().getPackageName() + ".GALLERY", galleries.get(0));
                runOnUi(() -> startActivity(intent));
            }
        }, true).start();
    }

    private void showDialogDownloadAll() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.download_all_galleries_in_this_page)
            .setIcon(R.drawable.ic_file)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                List<GenericGallery> galleries = inspector.getGalleries();
                if (galleries == null) return;
                for (GenericGallery g : galleries)
                    DownloadGalleryV2.downloadGallery(requireContext(), g);
            })
            .show();
    }
}
