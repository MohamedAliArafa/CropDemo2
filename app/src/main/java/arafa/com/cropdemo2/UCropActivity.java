package arafa.com.cropdemo2;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.yalantis.ucrop.Constants;
import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.callback.BitmapCropCallback;
import com.yalantis.ucrop.callback.BitmapLoadCallback;
import com.yalantis.ucrop.model.AspectRatio;
import com.yalantis.ucrop.model.ExifInfo;
import com.yalantis.ucrop.util.BitmapLoadUtils;
import com.yalantis.ucrop.util.SelectedStateListDrawable;
import com.yalantis.ucrop.view.CropImageView;
import com.yalantis.ucrop.view.GestureCropImageView;
import com.yalantis.ucrop.view.OverlayView;
import com.yalantis.ucrop.view.TransformImageView;
import com.yalantis.ucrop.view.UCropView;
import com.yalantis.ucrop.view.widget.AspectRatioTextView;
import com.yalantis.ucrop.view.widget.HorizontalProgressWheelView;
import com.zomato.photofilters.FilterPack;
import com.zomato.photofilters.imageprocessors.Filter;
import com.zomato.photofilters.imageprocessors.subfilters.BrightnessSubFilter;
import com.zomato.photofilters.utils.ThumbnailItem;
import com.zomato.photofilters.utils.ThumbnailsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import arafa.com.cropdemo2.executor.JobExecutor;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.yalantis.ucrop.UCrop.Options.ALL;
import static com.yalantis.ucrop.UCrop.Options.ROTATE;
import static com.yalantis.ucrop.UCrop.Options.SCALE;

/**
 * Created by Oleksii Shliama (https://github.com/shliama).
 */

@SuppressWarnings("ConstantConditions")
public class UCropActivity extends AppCompatActivity implements ThumbnailsAdapter.ThumbnailsAdapterListener , CropImageView.LowPixelsInteractions {

    public static final int DEFAULT_COMPRESS_QUALITY = 90;
    public static final Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG;

    @Inject
    JobExecutor threadExecutor;

    Bitmap mImageBitmap = null;
    Bitmap filteredBitmap = null;
    private ArrayList<ThumbnailItem> thumbnailItemList = new ArrayList<>();
    ThumbnailsAdapter mAdapter;

    float defaultScale, xCoor, yCoor;

    private boolean hasDefaultScale = false;

    static {
        System.loadLibrary("NativeImageProcessor");
    }

    private RecyclerView mFilterRecycler;

    public void prepareThumbnail(final Bitmap bitmap) {
        mAdapter = new ThumbnailsAdapter(UCropActivity.this,
                thumbnailItemList, UCropActivity.this);

        mFilterRecycler = findViewById(R.id.recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        layoutManager.scrollToPosition(0);
        mFilterRecycler.setLayoutManager(layoutManager);
        mFilterRecycler.setAdapter(mAdapter);
        Runnable r = new Runnable() {
            public void run() {
                Bitmap thumbImage;

                if (bitmap == null) {
                    thumbImage = null;
                } else {
                    thumbImage = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth(), bitmap.getHeight(), false);
                }

                if (thumbImage == null)
                    return;

                ThumbnailsManager.clearThumbs();
                thumbnailItemList.clear();

                // add normal bitmap first
                ThumbnailItem thumbnailItem = new ThumbnailItem();
                thumbnailItem.image = thumbImage;
                thumbnailItem.filterName = getString(com.yalantis.ucrop.R.string.ucrop_filter_normal);
                ThumbnailsManager.addThumb(thumbnailItem);

                List<Filter> filters = FilterPack.getFilterPack(getBaseContext());

                for (Filter filter : filters) {
                    ThumbnailItem tI = new ThumbnailItem();
                    tI.image = thumbImage;
                    tI.filter = filter;
                    tI.filterName = filter.getName();
                    ThumbnailsManager.addThumb(tI);
                }

                thumbnailItemList.addAll(ThumbnailsManager.processThumbs(getBaseContext()));

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        };

        new Thread(r).start();
    }

    FiltersListFragmentListener listener;

    public void setListener(FiltersListFragmentListener listener) {
        this.listener = listener;
    }

    @Override
    public void showHidePixelsImage(boolean show) {
        if (show){
            lowPixelsIv.setVisibility(View.VISIBLE);
        }else {
            lowPixelsIv.setVisibility(View.INVISIBLE);
        }
    }

    public interface FiltersListFragmentListener {
        void onFilterSelected(Filter filter);
    }

    @Override
    public void onFilterSelected(Filter filter) {
        if (mImageBitmap != null) {
            filterImage(filter).subscribe(new Observer() {
                @Override
                public void onSubscribe(Disposable d) {

                }

                @Override
                public void onNext(Object o) {

                }

                @Override
                public void onError(Throwable e) {

                }

                @Override
                public void onComplete() {
                    Toast.makeText(UCropActivity.this, "Completed", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (listener != null)
            listener.onFilterSelected(filter);
    }

    Observable filterImage(Filter filter) {
        return Observable.create(emitter -> {
            filteredBitmap = filter.processFilter(mImageBitmap.copy(Bitmap.Config.ARGB_8888, true));
            mGestureCropImageView.getViewBitmap().recycle();
            FileOutputStream out = null;
            String destinationFileName = "SimpleCache.png";
            File file = new File(getCacheDir(), destinationFileName);
            try {
                out = new FileOutputStream(file);
                filteredBitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                // PNG is a lossless format, the compression factor (100) is ignored
            } catch (Exception e) {
                emitter.onError(e);
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                    mGestureCropImageView.setImageBitmap(filteredBitmap);
                    emitter.onComplete();
                } catch (Exception e) {
                    emitter.onError(e);
                }
            }
        });
    }


    private static final String TAG = "UCropActivity";

    private static final int TABS_COUNT = 3;
    private static final int SCALE_WIDGET_SENSITIVITY_COEFFICIENT = 15000;
    private static final int ROTATE_WIDGET_SENSITIVITY_COEFFICIENT = 42;

    private String mToolbarTitle;

    // Enables dynamic coloring
    private int mToolbarColor;
    private int mStatusBarColor;
    private int mActiveWidgetColor;
    private int mToolbarWidgetColor;
    @ColorInt
    private int mRootViewBackgroundColor;
    @DrawableRes
    private int mToolbarCancelDrawable;
    @DrawableRes
    private int mToolbarCropDrawable;
    private int mLogoColor;

    private boolean mShowBottomControls;
    private boolean mShowLoader = true;

    private UCropView mUCropView;
    private GestureCropImageView mGestureCropImageView;
    private OverlayView mOverlayView;
    private ViewGroup mWrapperStateAspectRatio, mWrapperStateRotate, mWrapperStateScale,
            mWrapperStateFilter, mWrapperStateBrightness;
    private ViewGroup mLayoutAspectRatio, mLayoutRotate, mLayoutScale, mLayoutFilter,
            mLayoutBrightness;
    private List<ViewGroup> mCropAspectRatioViews = new ArrayList<>();
    private TextView mTextViewRotateAngle, mTextViewScalePercent, mTextViewBrightnessPercent;
    ImageView lowPixelsIv ;

    private View mBlockingView;

    private Bitmap.CompressFormat mCompressFormat = DEFAULT_COMPRESS_FORMAT;
    private int mCompressQuality = DEFAULT_COMPRESS_QUALITY;
    private int[] mAllowedGestures;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.yalantis.ucrop.R.layout.ucrop_activity_photobox);

        final Intent intent = getIntent();

        setupViews(intent);
        setImageData(intent);
        setInitialState();
        addBlockingView();


        setupDefaultScale(intent);
    }

    private void setupDefaultScale(Intent intent) {
        if (intent != null && intent.hasExtra(Constants.EXTRA_SCALE_DEFAULT)) {
            float defaultScale = intent.getFloatExtra(Constants.EXTRA_SCALE_DEFAULT, 0f);
            float xCoor = intent.getFloatExtra(Constants.EXTRA_XCOR_DEFAULT, 0f);
            float yCoor = intent.getFloatExtra(Constants.EXTRA_YCOR_DEFAULT, 0f);
            Log.e("DefaultScale", mGestureCropImageView.getCurrentScale() + defaultScale + "->");
            Log.e("yCoor", yCoor + "->");
            Log.e("xCoor", xCoor + "->");
            hasDefaultScale = true;

//            mGestureCropImageView.zoomInImage(defaultScale);
//            mGestureCropImageView.postScale(defaultScale, xCoor, yCoor);

        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(com.yalantis.ucrop.R.menu.ucrop_menu_activity, menu);

        // Change crop & loader menu icons color to match the rest of the UI colors

        MenuItem menuItemLoader = menu.findItem(com.yalantis.ucrop.R.id.menu_loader);
        Drawable menuItemLoaderIcon = menuItemLoader.getIcon();
        if (menuItemLoaderIcon != null) {
            try {
                menuItemLoaderIcon.mutate();
                menuItemLoaderIcon.setColorFilter(mToolbarWidgetColor, PorterDuff.Mode.SRC_ATOP);
                menuItemLoader.setIcon(menuItemLoaderIcon);
            } catch (IllegalStateException e) {
                Log.i(TAG, String.format("%s - %s", e.getMessage(), getString(com.yalantis.ucrop.R.string.ucrop_mutate_exception_hint)));
            }
            ((Animatable) menuItemLoader.getIcon()).start();
        }

        MenuItem menuItemCrop = menu.findItem(com.yalantis.ucrop.R.id.menu_crop);
        Drawable menuItemCropIcon = ContextCompat.getDrawable(this, mToolbarCropDrawable);
        if (menuItemCropIcon != null) {
            menuItemCropIcon.mutate();
            menuItemCropIcon.setColorFilter(mToolbarWidgetColor, PorterDuff.Mode.SRC_ATOP);
            menuItemCrop.setIcon(menuItemCropIcon);
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(com.yalantis.ucrop.R.id.menu_crop).setVisible(!mShowLoader);
        menu.findItem(com.yalantis.ucrop.R.id.menu_loader).setVisible(mShowLoader);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == com.yalantis.ucrop.R.id.menu_crop) {
            cropAndSaveImage();
        } else if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGestureCropImageView != null) {
            mGestureCropImageView.cancelAllAnimations();
        }
    }

    /**
     * This method extracts all data from the incoming intent and setups views properly.
     */
    private void setImageData(@NonNull Intent intent) {
        Uri inputUri = intent.getParcelableExtra(UCrop.EXTRA_INPUT_URI);
        Uri outputUri = intent.getParcelableExtra(UCrop.EXTRA_OUTPUT_URI);
        processOptions(intent);

        if (inputUri != null && outputUri != null) {
            try {
                mGestureCropImageView.setImageUri(inputUri, outputUri);
                BitmapLoadUtils.decodeBitmapInBackground(
                        getBaseContext(), inputUri, outputUri,
                        BitmapLoadUtils.calculateMaxBitmapSize(getBaseContext()),
                        BitmapLoadUtils.calculateMaxBitmapSize(getBaseContext()), new BitmapLoadCallback() {
                            @Override
                            public void onBitmapLoaded(@NonNull Bitmap bitmap, @NonNull ExifInfo exifInfo, @NonNull String imageInputPath, @Nullable String imageOutputPath) {
                                mImageBitmap = bitmap;
                                prepareThumbnail(bitmap);
                            }

                            @Override
                            public void onFailure(@NonNull Exception bitmapWorkerException) {

                            }

                            @Override
                            public void afterLoadComplete(float scale, float xCoor, float yCoor) {

                            }
                        });
            } catch (Exception e) {
                setResultError(e);
                finish();
            }
        } else {
            setResultError(new NullPointerException(getString(com.yalantis.ucrop.R.string.ucrop_error_input_data_is_absent)));
            finish();
        }
    }

    /**
     * This method extracts {@link UCrop.Options #optionsBundle} from incoming intent
     * and setups Activity, {@link OverlayView} and {@link CropImageView} properly.
     */
    @SuppressWarnings("deprecation")
    private void processOptions(@NonNull Intent intent) {
        // Bitmap compression options
        String compressionFormatName = intent.getStringExtra(UCrop.Options.EXTRA_COMPRESSION_FORMAT_NAME);
        Bitmap.CompressFormat compressFormat = null;
        if (!TextUtils.isEmpty(compressionFormatName)) {
            compressFormat = Bitmap.CompressFormat.valueOf(compressionFormatName);
        }
        mCompressFormat = (compressFormat == null) ? DEFAULT_COMPRESS_FORMAT : compressFormat;

        mCompressQuality = intent.getIntExtra(UCrop.Options.EXTRA_COMPRESSION_QUALITY, UCropActivity.DEFAULT_COMPRESS_QUALITY);

        // Gestures options
        int[] allowedGestures = intent.getIntArrayExtra(UCrop.Options.EXTRA_ALLOWED_GESTURES);
        if (allowedGestures != null && allowedGestures.length == TABS_COUNT) {
            mAllowedGestures = allowedGestures;
        }

        // Crop image view options
        mGestureCropImageView.setMaxBitmapSize(intent.getIntExtra(UCrop.Options.EXTRA_MAX_BITMAP_SIZE, CropImageView.DEFAULT_MAX_BITMAP_SIZE));
        mGestureCropImageView.setMaxScaleMultiplier(intent.getFloatExtra(UCrop.Options.EXTRA_MAX_SCALE_MULTIPLIER, CropImageView.DEFAULT_MAX_SCALE_MULTIPLIER));
        mGestureCropImageView.setImageToWrapCropBoundsAnimDuration(intent.getIntExtra(UCrop.Options.EXTRA_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION, CropImageView.DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION));

        // Overlay view options
        mOverlayView.setFreestyleCropEnabled(intent.getBooleanExtra(UCrop.Options.EXTRA_FREE_STYLE_CROP, OverlayView.DEFAULT_FREESTYLE_CROP_MODE != OverlayView.FREESTYLE_CROP_MODE_DISABLE));

        mOverlayView.setDimmedColor(intent.getIntExtra(UCrop.Options.EXTRA_DIMMED_LAYER_COLOR, getResources().getColor(com.yalantis.ucrop.R.color.ucrop_color_default_dimmed)));
        mOverlayView.setCircleDimmedLayer(intent.getBooleanExtra(UCrop.Options.EXTRA_CIRCLE_DIMMED_LAYER, OverlayView.DEFAULT_CIRCLE_DIMMED_LAYER));

        mOverlayView.setShowCropFrame(intent.getBooleanExtra(UCrop.Options.EXTRA_SHOW_CROP_FRAME, OverlayView.DEFAULT_SHOW_CROP_FRAME));
        mOverlayView.setCropFrameColor(intent.getIntExtra(UCrop.Options.EXTRA_CROP_FRAME_COLOR, getResources().getColor(com.yalantis.ucrop.R.color.ucrop_color_default_crop_frame)));
        mOverlayView.setCropFrameStrokeWidth(intent.getIntExtra(UCrop.Options.EXTRA_CROP_FRAME_STROKE_WIDTH, getResources().getDimensionPixelSize(com.yalantis.ucrop.R.dimen.ucrop_default_crop_frame_stoke_width)));
        mOverlayView.setCropFrameShadow(Color.BLACK);
        mOverlayView.setCropGridShadow(Color.BLACK);

        mOverlayView.setShowCropGrid(intent.getBooleanExtra(UCrop.Options.EXTRA_SHOW_CROP_GRID, OverlayView.DEFAULT_SHOW_CROP_GRID));
        mOverlayView.setCropGridRowCount(intent.getIntExtra(UCrop.Options.EXTRA_CROP_GRID_ROW_COUNT, OverlayView.DEFAULT_CROP_GRID_ROW_COUNT));
        mOverlayView.setCropGridColumnCount(intent.getIntExtra(UCrop.Options.EXTRA_CROP_GRID_COLUMN_COUNT, OverlayView.DEFAULT_CROP_GRID_COLUMN_COUNT));
        mOverlayView.setCropGridColor(intent.getIntExtra(UCrop.Options.EXTRA_CROP_GRID_COLOR, getResources().getColor(com.yalantis.ucrop.R.color.ucrop_color_default_crop_grid)));
        mOverlayView.setCropGridStrokeWidth(intent.getIntExtra(UCrop.Options.EXTRA_CROP_GRID_STROKE_WIDTH, getResources().getDimensionPixelSize(com.yalantis.ucrop.R.dimen.ucrop_default_crop_grid_stoke_width)));

        // Aspect ratio options
        float aspectRatioX = intent.getFloatExtra(UCrop.EXTRA_ASPECT_RATIO_X, 0);
        float aspectRatioY = intent.getFloatExtra(UCrop.EXTRA_ASPECT_RATIO_Y, 0);

        int aspectRationSelectedByDefault = intent.getIntExtra(UCrop.Options.EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, 0);
        ArrayList<AspectRatio> aspectRatioList = intent.getParcelableArrayListExtra(UCrop.Options.EXTRA_ASPECT_RATIO_OPTIONS);

        if (aspectRatioX > 0 && aspectRatioY > 0) {
            if (mWrapperStateAspectRatio != null) {
                mWrapperStateAspectRatio.setVisibility(View.GONE);
            }
            mGestureCropImageView.setTargetAspectRatio(aspectRatioX / aspectRatioY);
        } else if (aspectRatioList != null && aspectRationSelectedByDefault < aspectRatioList.size()) {
            mGestureCropImageView.setTargetAspectRatio(aspectRatioList.get(aspectRationSelectedByDefault).getAspectRatioX() /
                    aspectRatioList.get(aspectRationSelectedByDefault).getAspectRatioY());
        } else {
            mGestureCropImageView.setTargetAspectRatio(CropImageView.SOURCE_IMAGE_ASPECT_RATIO);
        }

        // Result bitmap max size options
        int maxSizeX = intent.getIntExtra(UCrop.EXTRA_MAX_SIZE_X, 0);
        int maxSizeY = intent.getIntExtra(UCrop.EXTRA_MAX_SIZE_Y, 0);

        if (maxSizeX > 0 && maxSizeY > 0) {
            mGestureCropImageView.setMaxResultImageSizeX(maxSizeX);
            mGestureCropImageView.setMaxResultImageSizeY(maxSizeY);
        }
    }


    private void setupViews(@NonNull Intent intent) {
        mStatusBarColor = intent.getIntExtra(UCrop.Options.EXTRA_STATUS_BAR_COLOR, ContextCompat.getColor(this, com.yalantis.ucrop.R.color.ucrop_color_statusbar));
        mToolbarColor = intent.getIntExtra(UCrop.Options.EXTRA_TOOL_BAR_COLOR, ContextCompat.getColor(this, com.yalantis.ucrop.R.color.ucrop_color_toolbar));
        mActiveWidgetColor = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_COLOR_WIDGET_ACTIVE, ContextCompat.getColor(this, com.yalantis.ucrop.R.color.ucrop_color_widget_active));
        mToolbarWidgetColor = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_WIDGET_COLOR_TOOLBAR, ContextCompat.getColor(this, com.yalantis.ucrop.R.color.ucrop_color_toolbar_widget));
        mToolbarCancelDrawable = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_WIDGET_CANCEL_DRAWABLE, com.yalantis.ucrop.R.drawable.ucrop_ic_cross);
        mToolbarCropDrawable = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_WIDGET_CROP_DRAWABLE, com.yalantis.ucrop.R.drawable.ucrop_ic_done);
        mToolbarTitle = intent.getStringExtra(UCrop.Options.EXTRA_UCROP_TITLE_TEXT_TOOLBAR);
        mToolbarTitle = mToolbarTitle != null ? mToolbarTitle : getResources().getString(com.yalantis.ucrop.R.string.ucrop_label_edit_photo);
        mLogoColor = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_LOGO_COLOR, ContextCompat.getColor(this, com.yalantis.ucrop.R.color.ucrop_color_default_logo));
        mShowBottomControls = !intent.getBooleanExtra(UCrop.Options.EXTRA_HIDE_BOTTOM_CONTROLS, false);
        mRootViewBackgroundColor = intent.getIntExtra(UCrop.Options.EXTRA_UCROP_ROOT_VIEW_BACKGROUND_COLOR, ContextCompat.getColor(this, com.yalantis.ucrop.R.color.ucrop_color_crop_background));

        setupAppBar();
        initiateRootViews();

        if (mShowBottomControls) {
            ViewGroup photoBox = findViewById(com.yalantis.ucrop.R.id.ucrop_photobox);
            View.inflate(this, com.yalantis.ucrop.R.layout.ucrop_controls, photoBox);

            mWrapperStateAspectRatio = findViewById(com.yalantis.ucrop.R.id.state_aspect_ratio);
            mWrapperStateAspectRatio.setOnClickListener(mStateClickListener);
            mWrapperStateRotate = findViewById(com.yalantis.ucrop.R.id.state_rotate);
            mWrapperStateRotate.setOnClickListener(mStateClickListener);
            mWrapperStateFilter = findViewById(com.yalantis.ucrop.R.id.state_filter);
            mWrapperStateFilter.setOnClickListener(mStateClickListener);
            mWrapperStateScale = findViewById(com.yalantis.ucrop.R.id.state_scale);
            mWrapperStateScale.setOnClickListener(mStateClickListener);
            mWrapperStateBrightness = findViewById(com.yalantis.ucrop.R.id.state_brightness);
            mWrapperStateBrightness.setOnClickListener(mStateClickListener);

            mLayoutAspectRatio = findViewById(com.yalantis.ucrop.R.id.layout_aspect_ratio);
            mLayoutRotate = findViewById(com.yalantis.ucrop.R.id.layout_rotate_wheel);
            mLayoutScale = findViewById(com.yalantis.ucrop.R.id.layout_scale_wheel);
            mLayoutFilter = findViewById(com.yalantis.ucrop.R.id.layout_filter_gallery);
            mLayoutBrightness = findViewById(com.yalantis.ucrop.R.id.layout_brightness_wheel);

            setupAspectRatioWidget(intent);
            setupRotateWidget();
            setupScaleWidget();
            setupStatesWrapper();
            setupBrightnessWidget();
        }
    }

    /**
     * Configures and styles both status bar and toolbar.
     */
    private void setupAppBar() {
        setStatusBarColor(mStatusBarColor);

        final Toolbar toolbar = findViewById(com.yalantis.ucrop.R.id.toolbar);

        // Set all of the Toolbar coloring
        toolbar.setBackgroundColor(mToolbarColor);
        toolbar.setTitleTextColor(mToolbarWidgetColor);

        final TextView toolbarTitle = toolbar.findViewById(com.yalantis.ucrop.R.id.toolbar_title);
        toolbarTitle.setTextColor(mToolbarWidgetColor);
        toolbarTitle.setText(mToolbarTitle);

        toolbarTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGestureCropImageView.zoomInImage(1.5f);
            }
        });

        // Color buttons inside the Toolbar
        Drawable stateButtonDrawable = ContextCompat.getDrawable(this, mToolbarCancelDrawable).mutate();
        stateButtonDrawable.setColorFilter(mToolbarWidgetColor, PorterDuff.Mode.SRC_ATOP);
        toolbar.setNavigationIcon(stateButtonDrawable);

        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }
    }

    private void initiateRootViews() {
        mUCropView = findViewById(com.yalantis.ucrop.R.id.ucrop);
        lowPixelsIv = findViewById(com.yalantis.ucrop.R.id.iv_lowPixels);

        mGestureCropImageView = mUCropView.getCropImageView();
        mGestureCropImageView.setLowPixelsInteractions(this);
        mOverlayView = mUCropView.getOverlayView();

        mGestureCropImageView.setTransformImageListener(mImageListener);

        ((ImageView) findViewById(com.yalantis.ucrop.R.id.image_view_logo)).setColorFilter(mLogoColor, PorterDuff.Mode.SRC_ATOP);

        findViewById(com.yalantis.ucrop.R.id.ucrop_frame).setBackgroundColor(mRootViewBackgroundColor);
    }

    private TransformImageView.TransformImageListener mImageListener = new TransformImageView.TransformImageListener() {
        @Override
        public void onRotate(float currentAngle) {
            setAngleText(currentAngle);
        }

        @Override
        public void onScale(float currentScale) {
            setScaleText(currentScale);

            mGestureCropImageView.getImageState();
            if (hasDefaultScale) {
                hasDefaultScale = false;
                onDrawNewScales(defaultScale, xCoor, yCoor);
            }
        }

        @Override
        public void onDrawNewScales(float currentScale, float xc, float yc) {
            mGestureCropImageView.postScale(currentScale, xc, yc);
        }

        @Override
        public void onLoadComplete() {
            mUCropView.animate().alpha(1).setDuration(300).setInterpolator(new AccelerateInterpolator());
            mBlockingView.setClickable(false);
            mShowLoader = false;
            supportInvalidateOptionsMenu();
        }

        @Override
        public void onLoadFailure(@NonNull Exception e) {
            setResultError(e);
            finish();
        }
    };

    /**
     * Use {@link #mActiveWidgetColor} for color filter
     */
    private void setupStatesWrapper() {
        ImageView stateScaleImageView = findViewById(com.yalantis.ucrop.R.id.image_view_state_scale);
        ImageView stateRotateImageView = findViewById(com.yalantis.ucrop.R.id.image_view_state_rotate);
        ImageView stateAspectRatioImageView = findViewById(com.yalantis.ucrop.R.id.image_view_state_aspect_ratio);

        stateScaleImageView.setImageDrawable(new SelectedStateListDrawable(stateScaleImageView.getDrawable(), mActiveWidgetColor));
        stateRotateImageView.setImageDrawable(new SelectedStateListDrawable(stateRotateImageView.getDrawable(), mActiveWidgetColor));
        stateAspectRatioImageView.setImageDrawable(new SelectedStateListDrawable(stateAspectRatioImageView.getDrawable(), mActiveWidgetColor));
    }


    /**
     * Sets status-bar color for L devices.
     *
     * @param color - status-bar color
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setStatusBarColor(@ColorInt int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Window window = getWindow();
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(color);
            }
        }
    }

    private void setupAspectRatioWidget(@NonNull Intent intent) {

        int aspectRationSelectedByDefault = intent.getIntExtra(UCrop.Options.EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, 0);
        ArrayList<AspectRatio> aspectRatioList = intent.getParcelableArrayListExtra(UCrop.Options.EXTRA_ASPECT_RATIO_OPTIONS);

        if (aspectRatioList == null || aspectRatioList.isEmpty()) {
            aspectRationSelectedByDefault = 0;

            aspectRatioList = new ArrayList<>();
            aspectRatioList.add(new AspectRatio("Original", 0, 1));
            aspectRatioList.add(new AspectRatio(null, 8, 8));
            aspectRatioList.add(new AspectRatio(null, 8, 12));
//            aspectRatioList.add(new AspectRatio(getString(R.string.ucrop_label_original).toUpperCase(),
//                    CropImageView.SOURCE_IMAGE_ASPECT_RATIO, CropImageView.SOURCE_IMAGE_ASPECT_RATIO));
            aspectRatioList.add(new AspectRatio(null, 12, 8));
            aspectRatioList.add(new AspectRatio(null, 12, 16));
            aspectRatioList.add(new AspectRatio(null, 24, 8));
            aspectRatioList.add(new AspectRatio(null, 36, 8));
        }

        LinearLayout wrapperAspectRatioList = findViewById(com.yalantis.ucrop.R.id.layout_aspect_ratio);

        FrameLayout wrapperAspectRatio;
        AspectRatioTextView aspectRatioTextView;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.weight = 1;
        for (AspectRatio aspectRatio : aspectRatioList) {
            wrapperAspectRatio = (FrameLayout) getLayoutInflater().inflate(com.yalantis.ucrop.R.layout.ucrop_aspect_ratio, null);
            wrapperAspectRatio.setLayoutParams(lp);
            aspectRatioTextView = ((AspectRatioTextView) wrapperAspectRatio.getChildAt(0));
            aspectRatioTextView.setActiveColor(mActiveWidgetColor);
            aspectRatioTextView.setAspectRatio(aspectRatio);

            wrapperAspectRatioList.addView(wrapperAspectRatio);
            mCropAspectRatioViews.add(wrapperAspectRatio);
        }

        mCropAspectRatioViews.get(aspectRationSelectedByDefault).setSelected(true);
        View v = mCropAspectRatioViews.get(aspectRationSelectedByDefault);
        v.setSelected(true);
        mGestureCropImageView.setTargetAspectRatio(
                ((AspectRatioTextView) ((ViewGroup) v).getChildAt(0)).getAspectRatio(v.isSelected()));
        mGestureCropImageView.setImageToWrapCropBounds();
        if (!v.isSelected()) {
            for (ViewGroup cropAspectRatioView : mCropAspectRatioViews) {
                cropAspectRatioView.setSelected(cropAspectRatioView == v);
            }
        }

        for (ViewGroup cropAspectRatioView : mCropAspectRatioViews) {
            cropAspectRatioView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mGestureCropImageView.setTargetAspectRatio(
                            ((AspectRatioTextView) ((ViewGroup) v).getChildAt(0)).getAspectRatio(v.isSelected()));
                    mGestureCropImageView.setImageToWrapCropBounds();
                    if (!v.isSelected()) {
                        for (ViewGroup cropAspectRatioView : mCropAspectRatioViews) {
                            cropAspectRatioView.setSelected(cropAspectRatioView == v);
                        }
                    }
                }
            });
        }
    }

    private void setupRotateWidget() {
        mTextViewRotateAngle = findViewById(com.yalantis.ucrop.R.id.text_view_rotate);
        ((HorizontalProgressWheelView) findViewById(com.yalantis.ucrop.R.id.rotate_scroll_wheel))
                .setScrollingListener(new HorizontalProgressWheelView.ScrollingListener() {
                    @Override
                    public void onScroll(float delta, float totalDistance) {
                        mGestureCropImageView.postRotate(delta / ROTATE_WIDGET_SENSITIVITY_COEFFICIENT);
                    }

                    @Override
                    public void onScrollEnd() {
                        mGestureCropImageView.setImageToWrapCropBounds();
                    }

                    @Override
                    public void onScrollStart() {
                        mGestureCropImageView.cancelAllAnimations();
                    }
                });

        ((HorizontalProgressWheelView) findViewById(com.yalantis.ucrop.R.id.rotate_scroll_wheel)).setMiddleLineColor(mActiveWidgetColor);


        findViewById(com.yalantis.ucrop.R.id.wrapper_reset_rotate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetRotation();
            }
        });
        findViewById(com.yalantis.ucrop.R.id.wrapper_rotate_by_angle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotateByAngle(90);
            }
        });
    }


    private void setupScaleWidget() {
        mTextViewScalePercent = findViewById(com.yalantis.ucrop.R.id.text_view_scale);
        ((HorizontalProgressWheelView) findViewById(com.yalantis.ucrop.R.id.scale_scroll_wheel))
                .setScrollingListener(new HorizontalProgressWheelView.ScrollingListener() {
                    @Override
                    public void onScroll(float delta, float totalDistance) {
                        if (delta > 0) {
                            mGestureCropImageView.zoomInImage(mGestureCropImageView.getCurrentScale()
                                    + delta * ((mGestureCropImageView.getMaxScale() - mGestureCropImageView.getMinScale()) / SCALE_WIDGET_SENSITIVITY_COEFFICIENT));
                        } else {
                            mGestureCropImageView.zoomOutImage(mGestureCropImageView.getCurrentScale()
                                    + delta * ((mGestureCropImageView.getMaxScale() - mGestureCropImageView.getMinScale()) / SCALE_WIDGET_SENSITIVITY_COEFFICIENT));
                        }
                    }

                    @Override
                    public void onScrollEnd() {
                        mGestureCropImageView.setImageToWrapCropBounds();
                    }

                    @Override
                    public void onScrollStart() {
                        mGestureCropImageView.cancelAllAnimations();
                    }
                });
        ((HorizontalProgressWheelView) findViewById(com.yalantis.ucrop.R.id.scale_scroll_wheel)).setMiddleLineColor(mActiveWidgetColor);
    }

    private float currentBrightness = 0;

    private void setupBrightnessWidget() {
        final Filter myFilter = new Filter();
        mTextViewBrightnessPercent = findViewById(com.yalantis.ucrop.R.id.text_view_brightness);
        ((HorizontalProgressWheelView) findViewById(com.yalantis.ucrop.R.id.brightness_scroll_wheel))
                .setScrollingListener(new HorizontalProgressWheelView.ScrollingListener() {
                    @Override
                    public void onScroll(float delta, float totalDistance) {
                        currentBrightness = currentBrightness
                                + delta * (200.0f / SCALE_WIDGET_SENSITIVITY_COEFFICIENT);

                    }

                    @Override
                    public void onScrollEnd() {
                        mGestureCropImageView.setImageToWrapCropBounds();
                        Filter myFilter = new Filter();
                        myFilter.addSubFilter(new BrightnessSubFilter((int) currentBrightness * 100));
                        filterImage(myFilter)
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Observer() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onNext(Object o) {

                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }

                                    @Override
                                    public void onComplete() {
                                        Toast.makeText(UCropActivity.this, "Completed: " + String.valueOf(currentBrightness * 100), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    @Override
                    public void onScrollStart() {
                        mGestureCropImageView.cancelAllAnimations();
                    }
                });
        ((HorizontalProgressWheelView) findViewById(com.yalantis.ucrop.R.id.brightness_scroll_wheel)).setMiddleLineColor(mActiveWidgetColor);
    }

//    private void setupFilterWidget() {
//        thumbnailItemList = new ArrayList<>();
//        RecyclerView.LayoutManager mLayoutManager =
//                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,
//                        false);
//        mFilterRecycler = (RecyclerView) findViewById(com.yalantis.ucrop.R.id.recycler_view);
//        mFilterRecycler.setLayoutManager(mLayoutManager);
//        mFilterRecycler.setItemAnimator(new DefaultItemAnimator());
//        mAdapter = new ThumbnailsAdapter(this, thumbnailItemList, this);
//        mFilterRecycler.setAdapter(mAdapter);
//    }

    private void setAngleText(float angle) {
        if (mTextViewRotateAngle != null) {
            mTextViewRotateAngle.setText(String.format(Locale.getDefault(), "%.1f°", angle));
        }
    }

    private void setScaleText(float scale) {
        if (mTextViewScalePercent != null) {
            mTextViewScalePercent.setText(String.format(Locale.getDefault(), "%d%%", (int) (scale * 100)));
        }
    }

    private void setBrightnessText(float scale) {
        if (mTextViewBrightnessPercent != null) {
            mTextViewBrightnessPercent.setText(String.format(Locale.getDefault(), "%d%%", (int) (scale * 100)));
        }
    }

    private void resetRotation() {
        mGestureCropImageView.postRotate(-mGestureCropImageView.getCurrentAngle());
        mGestureCropImageView.setImageToWrapCropBounds();
    }

    private void rotateByAngle(int angle) {
        mGestureCropImageView.postRotate(angle);
        mGestureCropImageView.setImageToWrapCropBounds();
    }

    private final View.OnClickListener mStateClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!v.isSelected()) {
                setWidgetState(v.getId());
            }
        }
    };

    private void setInitialState() {
        if (mShowBottomControls) {
            if (mWrapperStateAspectRatio.getVisibility() == View.VISIBLE) {
                setWidgetState(com.yalantis.ucrop.R.id.state_aspect_ratio);
            } else if (mWrapperStateScale.getVisibility() == View.VISIBLE) {
                setWidgetState(com.yalantis.ucrop.R.id.state_scale);
            } else if (mWrapperStateBrightness.getVisibility() == View.VISIBLE) {
                setWidgetState(com.yalantis.ucrop.R.id.state_brightness);
            } else {
                setWidgetState(com.yalantis.ucrop.R.id.state_filter);
            }
        } else {
            setAllowedGestures(0);
        }
    }

    private void setWidgetState(@IdRes int stateViewId) {
        if (!mShowBottomControls) return;

        mWrapperStateAspectRatio.setSelected(stateViewId == com.yalantis.ucrop.R.id.state_aspect_ratio);
        mWrapperStateRotate.setSelected(stateViewId == com.yalantis.ucrop.R.id.state_rotate);
        mWrapperStateScale.setSelected(stateViewId == com.yalantis.ucrop.R.id.state_scale);
        mWrapperStateFilter.setSelected(stateViewId == com.yalantis.ucrop.R.id.state_filter);
        mWrapperStateBrightness.setSelected(stateViewId == com.yalantis.ucrop.R.id.state_brightness);

        mLayoutAspectRatio.setVisibility(stateViewId == com.yalantis.ucrop.R.id.state_aspect_ratio ? View.VISIBLE : View.GONE);
        mLayoutRotate.setVisibility(stateViewId == com.yalantis.ucrop.R.id.state_rotate ? View.VISIBLE : View.GONE);
        mLayoutScale.setVisibility(stateViewId == com.yalantis.ucrop.R.id.state_scale ? View.VISIBLE : View.GONE);
        mLayoutFilter.setVisibility(stateViewId == com.yalantis.ucrop.R.id.state_filter ? View.VISIBLE : View.GONE);
        mLayoutBrightness.setVisibility(stateViewId == com.yalantis.ucrop.R.id.state_brightness ? View.VISIBLE : View.GONE);

        if (stateViewId == com.yalantis.ucrop.R.id.state_scale) {
            setAllowedGestures(0);
        } else if (stateViewId == com.yalantis.ucrop.R.id.state_rotate) {
            setAllowedGestures(1);
        } else {
            setAllowedGestures(2);
        }
    }

    private void setAllowedGestures(int tab) {
        mGestureCropImageView.setScaleEnabled(mAllowedGestures[tab] == ALL || mAllowedGestures[tab] == SCALE);
        mGestureCropImageView.setRotateEnabled(false);
//        mGestureCropImageView.setRotateEnabled(mAllowedGestures[tab] == ALL || mAllowedGestures[tab] == ROTATE);
    }

    /**
     * Adds view that covers everything below the Toolbar.
     * When it's clickable - user won't be able to click/touch anything below the Toolbar.
     * Need to block user input while loading and cropping an image.
     */
    private void addBlockingView() {
        if (mBlockingView == null) {
            mBlockingView = new View(this);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.addRule(RelativeLayout.BELOW, com.yalantis.ucrop.R.id.toolbar);
            mBlockingView.setLayoutParams(lp);
            mBlockingView.setClickable(true);
        }

        ((RelativeLayout) findViewById(com.yalantis.ucrop.R.id.ucrop_photobox)).addView(mBlockingView);
    }

    protected void cropAndSaveImage() {
        mBlockingView.setClickable(true);
        mShowLoader = true;
        supportInvalidateOptionsMenu();
        mGestureCropImageView.cropAndSaveImage(mCompressFormat, mCompressQuality, new BitmapCropCallback() {

            @Override
            public void onBitmapCropped(@NonNull Uri resultUri, int offsetX, int offsetY, int imageWidth, int imageHeight) {
                setResultUri(resultUri, mGestureCropImageView.getTargetAspectRatio(), offsetX, offsetY, imageWidth, imageHeight);
                finish();
            }

            @Override
            public void onCropFailure(@NonNull Throwable t) {
                setResultError(t);
                finish();
            }
        });
    }

    protected void setResultUri(Uri uri, float resultAspectRatio, int offsetX, int offsetY, int imageWidth, int imageHeight) {
        setResult(RESULT_OK, new Intent()
                .putExtra(UCrop.EXTRA_OUTPUT_URI, uri)
                .putExtra(UCrop.EXTRA_OUTPUT_CROP_ASPECT_RATIO, resultAspectRatio)
                .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_WIDTH, imageWidth)
                .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_HEIGHT, imageHeight)
                .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_X, offsetX)
                .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_Y, offsetY)
        );
    }

    protected void setResultError(Throwable throwable) {
        setResult(UCrop.RESULT_ERROR, new Intent().putExtra(UCrop.EXTRA_ERROR, throwable));
    }

}
