package cz.mzk.androidzoomifyviewer.examples;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import cz.mzk.androidzoomifyviewer.viewer.PointD;
import cz.mzk.androidzoomifyviewer.viewer.TiledImageView;
import cz.mzk.androidzoomifyviewer.viewer.TiledImageView.SingleTapListener;
import cz.mzk.androidzoomifyviewer.viewer.TiledImageView.ViewMode;

/**
 * @author Martin Řehánek
 */
public class FullscreenSingleImageActivity extends AppCompatActivity implements SingleTapListener, TiledImageView.MetadataInitializationHandler, TiledImageView.TileDownloadErrorListener {

    public static final String EXTRA_BASE_URL = "baseUrl";
    private static final String TAG = FullscreenSingleImageActivity.class.getSimpleName();
    // data
    private String mBaseUrl;
    // action bar
    private Toolbar mActionBar;
    private Spinner mViewModeSpinner;
    // tiled image
    private TiledImageView mImageView;
    // views to reflect state
    private View mProgressView;
    private View mErrorView;
    private TextView mErrorTitle;
    private TextView mErrorDescription;
    private TextView mErrorResourceUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");
        setContentView(R.layout.activity_fullscreen_single_image);
        mActionBar = (Toolbar) findViewById(R.id.action_bar);
        mViewModeSpinner = (Spinner) findViewById(R.id.viewModeSpinner);
        mProgressView = findViewById(R.id.progressView);
        mErrorView = findViewById(R.id.errorView);
        mErrorTitle = (TextView) findViewById(R.id.errorTitle);
        mErrorResourceUrl = (TextView) findViewById(R.id.errorResourceUrl);
        mErrorDescription = (TextView) findViewById(R.id.errorDescription);
        mImageView = (TiledImageView) findViewById(R.id.tiledImageView);
        mImageView.setMetadataInitializationHandler(this);
        mImageView.setTileDownloadErrorListener(this);
        mImageView.setSingleTapListener(this);
        mImageView.setViewMode(AppConfig.VIEW_MODE);

        loadActivityData(savedInstanceState, getIntent());
        initActionBar();
        showImage();
    }

    private void loadActivityData(Bundle savedInstanceState, Intent intent) {
        if (savedInstanceState != null) {
            loadActivityData(savedInstanceState);
        } else {
            loadActivityData(getIntent().getExtras());
        }
    }

    private void loadActivityData(Bundle bundle) {
        if (bundle != null) {
            Log.v(TAG, "loading activity data");
            if (bundle.containsKey(EXTRA_BASE_URL)) {
                mBaseUrl = bundle.getString(EXTRA_BASE_URL);
                // Log.d(TAG, "base url: '" + mBaseUrl + "'");
            }
        } else {
            Log.w(TAG, "bundle is null");
        }
    }

    private void initActionBar() {
        if (mActionBar != null) {
            setSupportActionBar(mActionBar);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayUseLogoEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            mActionBar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
            mActionBar.setNavigationOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            createViewModeSpinner();
        }
    }

    private void createViewModeSpinner() {
        mViewModeSpinner.setAdapter(new ArrayAdapter<ViewMode>(this, R.layout.menu_item_view_mode, ViewMode.values()));
        mViewModeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AppConfig.VIEW_MODE = ViewMode.values()[position];
                if (mImageView != null) {
                    mImageView.setViewMode(AppConfig.VIEW_MODE);
                    mImageView.loadImage(mBaseUrl);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // nothing
            }
        });
    }

    private void showImage() {
        Log.v(TAG, "showing image");
        mImageView.setVisibility(View.INVISIBLE);
        mProgressView.setVisibility(View.VISIBLE);
        mImageView.loadImage(mBaseUrl);
    }


    @Override
    protected void onResume() {
        super.onResume();
        mViewModeSpinner.setSelection(AppConfig.VIEW_MODE.ordinal());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(EXTRA_BASE_URL, mBaseUrl);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onMetadataInitialized() {
        mProgressView.setVisibility(View.INVISIBLE);
        mImageView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onMetadataUnhandableResponseCode(String imageMetadataUrl, int responseCode) {
        mProgressView.setVisibility(View.INVISIBLE);
        mErrorView.setVisibility(View.VISIBLE);
        mErrorTitle.setText("Cannot process server resource");
        mErrorResourceUrl.setText(imageMetadataUrl);
        mErrorDescription.setText("HTTP code: " + responseCode);
    }

    @Override
    public void onMetadataRedirectionLoop(String imageMetadataUrl, int redirections) {
        mProgressView.setVisibility(View.INVISIBLE);
        mErrorView.setVisibility(View.VISIBLE);
        mErrorTitle.setText("Redirection loop");
        mErrorResourceUrl.setText(imageMetadataUrl);
        mErrorDescription.setText("Too many redirections: " + redirections);
    }

    @Override
    public void onMetadataDataTransferError(String imageMetadataUrl, String errorMessage) {
        mProgressView.setVisibility(View.INVISIBLE);
        mErrorView.setVisibility(View.VISIBLE);
        mErrorTitle.setText("Data transfer error");
        mErrorResourceUrl.setText(imageMetadataUrl);
        mErrorDescription.setText(errorMessage);
    }

    @Override
    public void onMetadataInvalidData(String imageMetadataUrl, String errorMessage) {
        mProgressView.setVisibility(View.INVISIBLE);
        mErrorView.setVisibility(View.VISIBLE);
        mErrorTitle.setText("Invalid content in ZoomifyImageMetadata.xml");
        mErrorResourceUrl.setText(imageMetadataUrl);
        mErrorDescription.setText(errorMessage);
    }

    @Override
    public void onSingleTap(float x, float y, Rect boundingBox) {
        PointD point = new PointD(x, y);
        Toast.makeText(this, String.format("Single tap at %s, img: %s", point, boundingBox), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onTileUnhandableResponse(String tileImageUrl, int responseCode) {
        Toast.makeText(this, String.format("Failed to download tile from '%s': HTTP error: %d", tileImageUrl, responseCode), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTileRedirectionLoop(String tileImageUrl, int redirections) {
        Toast.makeText(this, String.format("Failed to download tile from '%s': Redirection loop, redirections %d", tileImageUrl, redirections), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTileDataTransferError(String tileImageUrl, String errorMessage) {
        Toast.makeText(this, String.format("Failed to download tile from '%s': Data transfer error: %d", tileImageUrl, errorMessage), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTileInvalidDataError(String tileImageUrl, String errorMessage) {
        Toast.makeText(this, String.format("Failed to download tile from '%s': Invalid data error: %d", tileImageUrl, errorMessage), Toast.LENGTH_SHORT).show();
    }

}
