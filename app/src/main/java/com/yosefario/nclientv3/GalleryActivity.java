package com.yosefario.nclientv3;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.core.content.FileProvider;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.yosefario.nclientv3.adapters.GalleryAdapter;
import com.yosefario.nclientv3.api.InspectorV3;
import com.yosefario.nclientv3.api.components.Gallery;
import com.yosefario.nclientv3.api.source.GalleryKey;
import com.yosefario.nclientv3.api.source.Source;
import com.yosefario.nclientv3.api.source.SourceId;
import com.yosefario.nclientv3.api.source.ehentai.EHentaiFavorites;
import com.yosefario.nclientv3.api.source.SourceManager;
import com.yosefario.nclientv3.api.source.ehentai.EHentaiAuth;
import com.yosefario.nclientv3.api.components.GenericGallery;
import com.yosefario.nclientv3.api.local.LocalGallery;
import com.yosefario.nclientv3.api.source.ehentai.EHentaiUrls;
import com.yosefario.nclientv3.async.database.Queries;
import com.yosefario.nclientv3.components.BlurTransformation;
import com.yosefario.nclientv3.components.GlideX;
import com.yosefario.nclientv3.components.activities.BaseActivity;
import com.yosefario.nclientv3.components.status.Status;
import com.yosefario.nclientv3.components.status.StatusManager;
import com.yosefario.nclientv3.components.views.RangeSelector;
import com.yosefario.nclientv3.components.widgets.CustomGridLayoutManager;
import com.yosefario.nclientv3.settings.AuthRequest;
import com.yosefario.nclientv3.settings.Favorites;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.settings.Login;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;
import yuku.ambilwarna.AmbilWarnaDialog;

public class GalleryActivity extends BaseActivity {
    @NonNull
    private GenericGallery gallery = Gallery.emptyGallery();
    private boolean isLocal;
    private GalleryAdapter adapter;
    private int zoom;
    private boolean isLocalFavorite;
    private MaterialToolbar toolbar;
    private MenuItem onlineFavoriteItem;
    private int favoriteSlot = EHentaiFavorites.NONE;
    private String statusString;

    private int newStatusColor;
    private String newStatusName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Global.initActivity(this);
        setContentView(R.layout.activity_gallery);

        // Guard against deep links when no source is configured
        if (!Global.isSourceConfigured() && getIntent().getData() != null) {
            Toast.makeText(this, R.string.source_configure_first, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (Global.isLockScreen())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int collapsedBar = com.google.android.material.color.MaterialColors.getColor(
            findViewById(R.id.drawer_layout), com.google.android.material.R.attr.colorSurfaceContainer);
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        drawerLayout.setStatusBarBackgroundColor(collapsedBar);

        AppBarLayout appBarLayout = findViewById(R.id.appbar);
        appBarLayout.setBackgroundColor(Color.TRANSPARENT);

        CollapsingToolbarLayout collapsing = findViewById(R.id.collapsing);
        collapsing.setStatusBarScrimColor(collapsedBar);
        collapsing.setContentScrimColor(collapsedBar);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        setupHeaderIconTint();
        recycler = findViewById(R.id.recycler);
        refresher = findViewById(R.id.refresher);
        masterLayout = findViewById(R.id.master_layout);
        GenericGallery gal = getIntent().getParcelableExtra(getPackageName() + ".GALLERY");
        if (gal == null && !tryLoadFromURL()) {
            finish();
            return;
        }
        if (gal != null) this.gallery = gal;
        if (gallery.getType() != GenericGallery.Type.LOCAL) {
            Queries.HistoryTable.addGallery(((Gallery) gallery).toSimpleGallery());
        }
        LogUtility.d("" + gallery);
        if (Global.useRtl()) recycler.setRotationY(180);
        isLocal = getIntent().getBooleanExtra(getPackageName() + ".ISLOCAL", false);
        zoom = getIntent().getIntExtra(getPackageName() + ".ZOOM", 0);
        refresher.setEnabled(false);
        recycler.setLayoutManager(new CustomGridLayoutManager(this, Global.getColumnCount()));

        loadGallery(gallery, zoom);//if already has gallery
    }

    private boolean tryLoadFromURL() {
        Uri data = getIntent().getData();
        GalleryKey key = data == null ? null : EHentaiUrls.parseGalleryLink(data.toString());
        if (key != null) {
            openFromLink(key.id(), key.source(), key.token());
            return true;
        }
        if (data != null && data.getPathSegments().size() >= 2) {//if using an URL
            List<String> params = data.getPathSegments();
            LogUtility.d(params.size() + ": " + params);
            int id;
            try {//if not an id return
                id = Integer.parseInt(params.get(1));
            } catch (NumberFormatException ignore) {
                return false;
            }
            if (params.size() > 2) {//check if it has a specific page
                try {
                    zoom = Integer.parseInt(params.get(2));
                } catch (NumberFormatException e) {
                    LogUtility.e(e.getLocalizedMessage(), e);
                    zoom = 0;
                }
            }
            openFromLink(id, null, null);
            return true;
        }
        return false;
    }

    private void openFromLink(int id, @Nullable SourceId source, @Nullable String token) {
        InspectorV3.galleryInspector(this, id, source, token, new InspectorV3.DefaultInspectorResponse() {
            @Override
            public void onSuccess(List<GenericGallery> galleries) {
                if (galleries.size() > 0) {
                    Intent intent = new Intent(GalleryActivity.this, GalleryActivity.class);
                    intent.putExtra(getPackageName() + ".GALLERY", galleries.get(0));
                    intent.putExtra(getPackageName() + ".ZOOM", zoom);
                    startActivity(intent);
                }
                finish();
            }
        }).start();
    }

    private void lookup() {
        CustomGridLayoutManager manager = (CustomGridLayoutManager) recycler.getLayoutManager();
        GalleryAdapter adapter = (GalleryAdapter) recycler.getAdapter();
        manager.setSpanSizeLookup(new CustomGridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.positionToType(position) == GalleryAdapter.Type.PAGE ? 1 : manager.getSpanCount();
            }
        });
    }

    private void loadGallery(GenericGallery gall, int zoom) {
        this.gallery = gall;
        if (getSupportActionBar() != null) {
            applyTitle();
        }
        loadBlurredCover();
        adapter = new GalleryAdapter(this, gallery, Global.getColumnCount());
        recycler.setAdapter(adapter);
        lookup();
        if (zoom > 0 && Global.getDownloadPolicy() != Global.DataUsageType.NONE) {
            Intent intent = new Intent(this, ZoomActivity.class);
            intent.putExtra(getPackageName() + ".GALLERY", this.gallery);
            intent.putExtra(getPackageName() + ".DIRECTORY", adapter.getDirectory());
            intent.putExtra(getPackageName() + ".PAGE", zoom);
            startActivity(intent);
        }
        checkBookmark();
    }

    private void checkBookmark() {
        int page = Queries.ResumeTable.pageFromId(gallery.getId());
        if (page < 0) return;
        Snackbar snack = Snackbar.make(toolbar, getString(R.string.resume_from_page, page), Snackbar.LENGTH_LONG);
        //Should be already compensated
        snack.setAction(R.string.resume, v -> new Thread(() -> {
            runOnUiThread(() -> recycler.scrollToPosition(page));
            if (Global.getColumnCount() != 1) return;
            Utility.threadSleep(500);
            runOnUiThread(() -> recycler.scrollToPosition(page));
        }).start());
        snack.show();
    }

    private void loadBlurredCover() {
        ImageView headerCover = findViewById(R.id.header_cover);
        if (headerCover == null) return;

        RequestManager glide = GlideX.with(this);
        if (glide == null) return;

        if (gallery instanceof Gallery) {
            Gallery g = (Gallery) gallery;
            Uri coverUri = g.getCover();

            RequestBuilder<Drawable> primary = glide.load(coverUri)
                .transform(new CenterCrop(), new BlurTransformation(15, 2));

            if (g.getSource() == SourceId.NHENTAI) {
                String base = String.format(Locale.US,
                    "https://t.%s/galleries/%d/cover", Utility.getHost(), g.getMediaId());
                RequestBuilder<Drawable> webpFallback = glide.load(base + ".webp.webp")
                    .transform(new CenterCrop(), new BlurTransformation(15, 2));
                RequestBuilder<Drawable> pngFallback = glide.load(base + ".png.webp")
                    .transform(new CenterCrop(), new BlurTransformation(15, 2))
                    .error(webpFallback);
                RequestBuilder<Drawable> jpgFallback = glide.load(base + ".jpg.webp")
                    .transform(new CenterCrop(), new BlurTransformation(15, 2))
                    .error(pngFallback);
                primary = primary.error(jpgFallback);
            }

            primary
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                .into(headerCover);
        } else if (gallery instanceof LocalGallery) {
            java.io.File firstPage = ((LocalGallery) gallery).getPage(1);
            if (firstPage != null) {
                glide.load(firstPage)
                    .transform(new CenterCrop(), new BlurTransformation(15, 2))
                    .into(headerCover);
            }
        }
    }

    private void applyTitle() {
        CollapsingToolbarLayout collapsing = findViewById(R.id.collapsing);
        ActionBar actionBar = getSupportActionBar();
        final String title = gallery.getTitle();
        if (collapsing == null || actionBar == null) return;
        View.OnLongClickListener listener = v -> {
            CopyToClipboardActivity.copyTextToClipboard(GalleryActivity.this, title);
            GalleryActivity.this.runOnUiThread(
                () -> Toast.makeText(GalleryActivity.this, R.string.title_copied_to_clipboard, Toast.LENGTH_SHORT).show()
            );
            return true;
        };

        collapsing.setOnLongClickListener(listener);
        findViewById(R.id.toolbar).setOnLongClickListener(listener);

        collapsing.setTitle(title);
        android.widget.TextView expandedTitle = findViewById(R.id.expanded_title);
        if (expandedTitle != null) {
            expandedTitle.setText(title);
            expandedTitle.setOnLongClickListener(listener);
        }
    }

    @Override
    protected int getPortraitColumnCount() {
        return 0;
    }

    @Override
    protected int getLandscapeColumnCount() {
        return 0;
    }

    private void applyOnlineFavoriteAppearance(MenuItem item, boolean onlineFavorite, boolean unknown) {
        item.setIcon(onlineFavorite ? R.drawable.ic_star : R.drawable.ic_star_border);
        if (unknown) item.setTitle(R.string.toggle_online_favorite);
        else if (onlineFavorite) item.setTitle(R.string.remove_from_online_favorites);
        else item.setTitle(R.string.add_to_online_favorite);
    }

    private void applyLocalFavoriteAppearance(MenuItem item, boolean localFavorite) {
        item.setIcon(localFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        item.setTitle(R.string.toggle_favorite);
    }

    private boolean isSourceLoggedIn(SourceId source) {
        return SourceManager.get(source).isLoggedIn();
    }

    public void initFavoriteIcon(Menu menu) {
        boolean isLogged = isSourceLoggedIn(gallery.getSource());
        boolean isValidOnline = gallery.isValid() && !isLocal;
        boolean onlineFavorite = !isLocal && ((Gallery) gallery).isOnlineFavorite();
        favoriteSlot = onlineFavorite ? Math.max(0, ((Gallery) gallery).getFavoriteSlot()) : EHentaiFavorites.NONE;
        boolean unknown = getIntent().getBooleanExtra(getPackageName() + ".UNKNOWN", false);

        MenuItem localItem = menu.findItem(R.id.favorite_manager);
        MenuItem onlineItem = menu.findItem(R.id.add_online_gallery);
        onlineFavoriteItem = onlineItem;

        boolean localVisible = isValidOnline;
        boolean sourceHasOnlineFav = SourceManager
            .get(gallery.getSource()).capabilities().hasOnlineFavorites();
        boolean onlineVisible = isValidOnline && isLogged && sourceHasOnlineFav;
        localItem.setVisible(localVisible);
        onlineItem.setVisible(onlineVisible);

        applyLocalFavoriteAppearance(localItem, isLocalFavorite);
        applyOnlineFavoriteAppearance(onlineItem, onlineFavorite, unknown);

        boolean onlineInToolbar;
        if (Global.isDefaultFavoriteOnline() && onlineVisible) onlineInToolbar = true;
        else if (!Global.isDefaultFavoriteOnline() && localVisible) onlineInToolbar = false;
        else onlineInToolbar = onlineVisible;
        placeFavorite(onlineItem, onlineInToolbar);
        placeFavorite(localItem, !onlineInToolbar);

        boolean categories = onlineVisible && SourceManager
            .get(gallery.getSource()).capabilities().hasFavoriteCategories();
        MenuItem categoryItem = menu.findItem(R.id.online_favorite_category);
        if (categoryItem != null) categoryItem.setVisible(categories);
        if (categories) {
            onlineItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            onlineItem.setActionView(null);
        }
    }

    private void placeFavorite(MenuItem item, boolean inToolbar) {
        item.setShowAsAction(inToolbar ? MenuItem.SHOW_AS_ACTION_IF_ROOM : MenuItem.SHOW_AS_ACTION_NEVER);
    }

    private final Runnable starHold = () -> pickFavoriteCategory();
    private final int[] starPos = new int[2];

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View star = findViewById(R.id.add_online_gallery);
        boolean watch = star != null && star.isShown() && gallery != null && gallery.isValid()
            && SourceManager.get(gallery.getSource()).capabilities().hasFavoriteCategories();
        if (!watch) return super.dispatchTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                star.getLocationOnScreen(starPos);
                if (ev.getRawX() >= starPos[0] && ev.getRawX() <= starPos[0] + star.getWidth()
                    && ev.getRawY() >= starPos[1] && ev.getRawY() <= starPos[1] + star.getHeight()) {
                    star.postDelayed(starHold, ViewConfiguration.getLongPressTimeout());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                star.removeCallbacks(starHold);
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private Menu toolbarMenu;
    private int headerIconColor = Color.WHITE;

    private void setupHeaderIconTint() {
        AppBarLayout appbar = findViewById(R.id.appbar);
        View expandedTitle = findViewById(R.id.expanded_title);
        int onSurface = com.google.android.material.color.MaterialColors.getColor(
            appbar, com.google.android.material.R.attr.colorOnSurface);
        appbar.addOnOffsetChangedListener((AppBarLayout.OnOffsetChangedListener) (bar, offset) -> {
            int range = bar.getTotalScrollRange();
            float fraction = range == 0 ? 0f : Math.abs(offset) / (float) range;
            if (expandedTitle != null) expandedTitle.setAlpha(Math.max(0f, 1f - fraction / 0.7f));
            int color = fraction > 0.5f ? onSurface : Color.WHITE;
            if (color != headerIconColor) {
                headerIconColor = color;
                applyHeaderIconTint();
            }
        });
    }

    private void applyHeaderIconTint() {
        if (toolbar == null) return;
        tintHeaderIcon(toolbar.getNavigationIcon());
        tintHeaderIcon(toolbar.getOverflowIcon());
        if (toolbarMenu != null)
            for (int i = 0; i < toolbarMenu.size(); i++)
                tintHeaderIcon(toolbarMenu.getItem(i).getIcon());
    }

    private void tintHeaderIcon(Drawable d) {
        if (d != null) androidx.core.graphics.drawable.DrawableCompat.setTint(d.mutate(), headerIconColor);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gallery, menu);
        isLocalFavorite = Favorites.isFavorite(gallery);

        menuItemsVisible(menu);
        initFavoriteIcon(menu);
        toolbarMenu = menu;
        applyHeaderIconTint();
        updateColumnCount(false);
        checkOnlineFavoriteStatus();
        return true;
    }

    private void checkOnlineFavoriteStatus() {
        if (isLocal || !gallery.isValid() || !Login.isLogged()) return;
        if (gallery.getSource() != SourceId.NHENTAI) return;
        String url = String.format(Locale.US, Utility.getBaseUrl() + "api/v2/galleries/%d/favorite", gallery.getId());
        Request request = new Request.Builder().url(url).get().build();
        Global.client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) return;
                    boolean favorited = new JSONObject(r.body().string()).optBoolean("favorited", false);
                    updateIcon(favorited);
                } catch (JSONException ignored) {
                }
            }
        });
    }

    private void menuItemsVisible(Menu menu) {
        boolean isLogged = Login.isLogged();
        boolean isValidOnline = gallery.isValid() && !isLocal;
        boolean isNhentai = gallery.getSource() == SourceId.NHENTAI;
        menu.findItem(R.id.download_gallery).setVisible(isValidOnline);
        menu.findItem(R.id.related).setVisible(isValidOnline && isNhentai);
        menu.findItem(R.id.comments).setVisible(isValidOnline);
        menu.findItem(R.id.download_torrent).setVisible(isLogged && isNhentai);

        menu.findItem(R.id.share).setVisible(gallery.isValid());
        menu.findItem(R.id.load_internet).setVisible(isLocal && gallery.isValid());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateColumnCount(false);
        if (isLocal) supportInvalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.download_gallery) {
            if (Global.hasStoragePermission(this))
                new RangeSelector(this, (Gallery) gallery).show();
            else
                requestStorage();
        } else if (id == R.id.add_online_gallery) {
            addToFavorite(item);
        } else if (id == R.id.online_favorite_category) {
            pickFavoriteCategory();
        } else if (id == R.id.change_view) updateColumnCount(true);
        else if (id == R.id.download_torrent) downloadTorrent();
        else if (id == R.id.load_internet) toInternet();
        else if (id == R.id.manage_status) updateStatus();
        else if (id == R.id.share) Global.shareGallery(this, gallery);
        else if (id == R.id.comments) {
            Intent i = new Intent(this, CommentActivity.class);
            i.putExtra(getPackageName() + ".GALLERYID", gallery.getId());
            i.putExtra(getPackageName() + ".SOURCE", gallery.getSource().ordinal());
            i.putExtra(getPackageName() + ".TOKEN", gallery.getToken());
            startActivity(i);
        } else if (id == R.id.related) {
            recycler.smoothScrollToPosition(recycler.getAdapter().getItemCount());
        } else if (id == R.id.favorite_manager) {
            toggleLocalFavorite(item);
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void downloadTorrent() {
        if(!Global.hasStoragePermission(this)){
            return;
        }

        String url = String.format(Locale.US, Utility.getBaseUrl() + "g/%d/download", gallery.getId());
        String referer = String.format(Locale.US, Utility.getBaseUrl() + "g/%d/", gallery.getId());

        new AuthRequest(referer, url, new Callback() {
            @Override
            public void onFailure(@NonNull Call call,@NonNull  IOException e) {
                GalleryActivity.this.runOnUiThread(() ->
                    Toast.makeText(GalleryActivity.this, R.string.failed, Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call,@NonNull Response response) throws IOException {
                File file=new File(Global.TORRENTFOLDER,gallery.getId()+".torrent");
                Utility.writeStreamToFile(response.body().byteStream(), file);
                Intent intent=new Intent(Intent.ACTION_VIEW);
                Uri torrentUri;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    torrentUri = FileProvider.getUriForFile(GalleryActivity.this, GalleryActivity.this.getPackageName() + ".provider", file);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }else{
                    torrentUri=Uri.fromFile(file);
                }
                intent.setDataAndType(torrentUri, "application/x-bittorrent");
                try {
                    GalleryActivity.this.startActivity(intent);
                }catch (RuntimeException ignore){
                    runOnUiThread(() ->
                        Toast.makeText(GalleryActivity.this, R.string.failed, Toast.LENGTH_SHORT).show()
                    );

                }
                file.deleteOnExit();
            }
        }).setMethod("GET",null).start();
    }

    private void updateStatus() {
        List<String> statuses = StatusManager.getNames();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        statusString = Queries.StatusMangaTable.getStatus(gallery.getId()).name;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice, statuses) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                CheckedTextView textView = (CheckedTextView) super.getView(position, convertView, parent);
                textView.setTextColor(StatusManager.getByName(statuses.get(position)).opaqueColor());
                return textView;
            }
        };
        builder.setSingleChoiceItems(adapter, statuses.indexOf(statusString), (dialog, which) -> statusString = statuses.get(which));
        builder
            .setNeutralButton(R.string.add, (dialog, which) -> createNewStatusDialog())
            .setNegativeButton(R.string.remove_status, (dialog, which) -> Queries.StatusMangaTable.remove(gallery.getId()))
            .setPositiveButton(R.string.ok, (dialog, which) -> Queries.StatusMangaTable.insert(gallery, statusString))
            .setTitle(R.string.change_status_title)
            .show();
    }

    private void createNewStatusDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        LinearLayout layout = (LinearLayout) View.inflate(this, R.layout.dialog_add_status, null);
        EditText name = layout.findViewById(R.id.name);
        Button btnColor = layout.findViewById(R.id.color);
        do {
            newStatusColor = Utility.RANDOM.nextInt() | 0xff000000;
        } while (newStatusColor == Color.BLACK || newStatusColor == Color.WHITE);
        btnColor.setBackgroundColor(newStatusColor);
        btnColor.setOnClickListener(v -> new AmbilWarnaDialog(GalleryActivity.this, newStatusColor, false, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                if (color == Color.WHITE || color == Color.BLACK) {
                    Toast.makeText(GalleryActivity.this, R.string.invalid_color_selected, Toast.LENGTH_SHORT).show();
                    return;
                }
                newStatusColor = color;
                btnColor.setBackgroundColor(color);
            }
        }).show());
        builder.setView(layout);
        builder.setTitle(R.string.create_new_status);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String newName = name.getText().toString();
            if (newName.length() < 2) {
                Toast.makeText(this, R.string.name_too_short, Toast.LENGTH_SHORT).show();
                return;
            }
            if (StatusManager.getByName(newName) != null) {
                Toast.makeText(this, R.string.duplicated_name, Toast.LENGTH_SHORT).show();
                return;
            }
            Status status = StatusManager.add(name.getText().toString(), newStatusColor);
            Queries.StatusMangaTable.insert(gallery, status);
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> updateStatus());
        builder.setOnCancelListener(dialog -> updateStatus());
        builder.show();
    }

    private void updateIcon(boolean nowIsFavorite) {
        GalleryActivity.this.runOnUiThread(() -> {
            onlineFavoriteItem.setIcon(!nowIsFavorite ? R.drawable.ic_star_border : R.drawable.ic_star);
            onlineFavoriteItem.setTitle(!nowIsFavorite ? R.string.add_to_online_favorite : R.string.remove_from_online_favorites);
            tintHeaderIcon(onlineFavoriteItem.getIcon());
        });
    }

    private void toggleLocalFavorite(MenuItem item) {
        if (isLocalFavorite) {
            if (Favorites.removeFavorite(gallery)) isLocalFavorite = !isLocalFavorite;
        } else if (Favorites.addFavorite((Gallery) gallery)) {
            isLocalFavorite = !isLocalFavorite;
        }
        item.setIcon(isLocalFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        tintHeaderIcon(item.getIcon());
    }

    private void addToFavorite(final MenuItem item) {
        setFavoriteSlot(favoriteSlot >= 0 ? EHentaiFavorites.NONE : -2, false);
    }

    private void setFavoriteSlot(int slot, boolean fromDialog) {
        if (slot == -2) slot = gallery.getSource() == SourceId.NHENTAI ? 0 : EHentaiFavorites.addSlot();
        final int target = slot;
        final MenuItem item = onlineFavoriteItem;
        if (item != null) item.setEnabled(false);
        new Thread(() -> {
            try {
                int now = SourceManager.get(gallery.getSource()).setOnlineFavoriteSlot(this,
                    new GalleryKey(gallery.getSource(), gallery.getId(), gallery.getToken()), target);
                if (now < EHentaiFavorites.NONE) now = target;
                favoriteSlot = now;
                ((Gallery) gallery).setFavoriteSlot(now);
                updateIcon(now >= 0);
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.failed, Toast.LENGTH_SHORT).show());
            } finally {
                if (item != null) runOnUiThread(() -> item.setEnabled(true));
            }
        }).start();
    }

    private void pickFavoriteCategory() {
        if (!gallery.isValid() || isLocal) return;
        new Thread(() -> {
            if (!EHentaiFavorites.namesFresh()) EHentaiFavorites.refreshNames(gallery.getSource() == SourceId.EXHENTAI);
            runOnUiThread(() -> {
                String[] labels = new String[EHentaiFavorites.SLOT_COUNT];
                for (int i = 0; i < labels.length; i++) labels[i] = EHentaiFavorites.label(i);
                new AlertDialog.Builder(this)
                    .setTitle(R.string.add_to_online_favorite)
                    .setSingleChoiceItems(labels, favoriteSlot, (dialog, which) -> {
                        setFavoriteSlot(which, true);
                        dialog.dismiss();
                    })
                    .setNeutralButton(R.string.remove_from_online_favorites, (dialog, which) -> {
                        setFavoriteSlot(EHentaiFavorites.NONE, true);
                    })
                    .show();
            });
        }).start();
    }

    private void updateColumnCount(boolean increase) {
        int x = Global.getColumnCount();
        CustomGridLayoutManager manager = (CustomGridLayoutManager) recycler.getLayoutManager();
        if (manager == null) return;
        MenuItem item = (( MaterialToolbar) findViewById(R.id.toolbar)).getMenu().findItem(R.id.change_view);
        if (increase || manager.getSpanCount() != x) {
            if (increase) x = x % 4 + 1;
            int pos = manager.findFirstVisibleItemPosition();
            Global.updateColumnCount(this, x);

            recycler.setLayoutManager(new CustomGridLayoutManager(this, x));
            LogUtility.d("Span count: " + manager.getSpanCount());
            if (adapter != null) {
                adapter.setColCount(Global.getColumnCount());
                recycler.setAdapter(adapter);
                lookup();
                recycler.scrollToPosition(pos);
                adapter.setMaxImageSize(null);

            }
        }

        if (item != null) {
            switch (x) {
                case 1:
                    item.setIcon(R.drawable.ic_view_1);
                    break;
                case 2:
                    item.setIcon(R.drawable.ic_view_2);
                    break;
                case 3:
                    item.setIcon(R.drawable.ic_view_3);
                    break;
                case 4:
                    item.setIcon(R.drawable.ic_view_4);
                    break;
            }
            tintHeaderIcon(item.getIcon());

        }
    }

    private void toInternet() {
        refresher.setEnabled(true);
        InspectorV3.galleryInspector(this, gallery.getId(), new InspectorV3.DefaultInspectorResponse() {
            @Override
            public void onSuccess(List<GenericGallery> galleries) {
                if (galleries.size() == 0) return;
                Intent intent = new Intent(GalleryActivity.this, GalleryActivity.class);
                LogUtility.d(galleries.get(0).toString());
                intent.putExtra(getPackageName() + ".GALLERY", galleries.get(0));
                runOnUiThread(() -> startActivity(intent));
            }
        }).start();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void requestStorage() {
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Global.initStorage(this);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            new RangeSelector(this, (Gallery) gallery).show();
    }
}
