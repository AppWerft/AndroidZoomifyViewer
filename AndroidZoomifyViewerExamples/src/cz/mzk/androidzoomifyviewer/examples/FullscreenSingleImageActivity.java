package cz.mzk.androidzoomifyviewer.examples;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import cz.mzk.androidzoomifyviewer.viewer.PointD;
import cz.mzk.androidzoomifyviewer.viewer.TiledImageView;
import cz.mzk.androidzoomifyviewer.viewer.TiledImageView.LoadingHandler;
import cz.mzk.androidzoomifyviewer.viewer.TiledImageView.SingleTapListener;

/**
 * @author Martin Řehánek
 * 
 */
public class FullscreenSingleImageActivity extends Activity implements LoadingHandler, SingleTapListener {

	private static final String TAG = FullscreenSingleImageActivity.class.getSimpleName();

	public static final String EXTRA_BASE_URL = "baseUrl";

	private TiledImageView mImageView;

	// Views to reflect TiledImageView state
	private View mProgressView;
	private View mErrorView;
	private TextView mErrorTitle;
	private TextView mErrorDescription;
	private TextView mErrorResourceUrl;

	private String mBaseUrl;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_fullscreen_single_image);
		if (savedInstanceState != null) {
			restoreData(savedInstanceState);
		} else {
			restoreData(getIntent().getExtras());
		}
		mProgressView = findViewById(R.id.progressView);
		mErrorView = findViewById(R.id.errorView);
		mErrorTitle = (TextView) findViewById(R.id.errorTitle);
		mErrorResourceUrl = (TextView) findViewById(R.id.errorResourceUrl);
		mErrorDescription = (TextView) findViewById(R.id.errorDescription);
		mImageView = (TiledImageView) findViewById(R.id.tiledImageView);
		mImageView.setLoadingHandler(this);
		mImageView.setSingleTapListener(this);
		showImage();
	}

	@Override
	protected void onStop() {
		mImageView.cancelUnnecessaryTasks();
		super.onStop();
	}

	private void restoreData(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			Log.d(TAG, "restoring");
			if (savedInstanceState.containsKey(EXTRA_BASE_URL)) {
				mBaseUrl = savedInstanceState.getString(EXTRA_BASE_URL);
				Log.d(TAG, "base url: '" + mBaseUrl + "'");
			}
		} else {
			Log.d(TAG, "bundle is null");
		}
	}

	private void showImage() {
		mImageView.setVisibility(View.INVISIBLE);
		mProgressView.setVisibility(View.VISIBLE);
		mImageView.loadImage(mBaseUrl);

	}

	@Override
	public void onImagePropertiesProcessed(String imagePropertiesUrl) {
		mProgressView.setVisibility(View.INVISIBLE);
		mImageView.setVisibility(View.VISIBLE);
	}

	@Override
	public void onImagePropertiesInvalidStateError(String imagePropertiesUrl, int responseCode) {
		mProgressView.setVisibility(View.INVISIBLE);
		mErrorView.setVisibility(View.VISIBLE);
		mErrorTitle.setText("Cannot process server resource");
		mErrorResourceUrl.setText(imagePropertiesUrl);
		mErrorDescription.setText("HTTP code: " + responseCode);
	}

	@Override
	public void onImagePropertiesRedirectionLoopError(String imagePropertiesUrl, int redirections) {
		mProgressView.setVisibility(View.INVISIBLE);
		mErrorView.setVisibility(View.VISIBLE);
		mErrorTitle.setText("Redirection loop");
		mErrorResourceUrl.setText(imagePropertiesUrl);
		mErrorDescription.setText("Too many redirections: " + redirections);
	}

	@Override
	public void onImagePropertiesDataTransferError(String imagePropertiesUrl, String errorMessage) {
		mProgressView.setVisibility(View.INVISIBLE);
		mErrorView.setVisibility(View.VISIBLE);
		mErrorTitle.setText("Data transfer error");
		mErrorResourceUrl.setText(imagePropertiesUrl);
		mErrorDescription.setText(errorMessage);
	}

	@Override
	public void onImagePropertiesInvalidDataError(String imagePropertiesUrl, String errorMessage) {
		mProgressView.setVisibility(View.INVISIBLE);
		mErrorView.setVisibility(View.VISIBLE);
		mErrorTitle.setText("Invalid content in ImageProperties.xml");
		mErrorResourceUrl.setText(imagePropertiesUrl);
		mErrorDescription.setText(errorMessage);
	}

	@Override
	public void onSingleTap(float x, float y) {
		PointD point = new PointD(x, y);
		Toast.makeText(this, "Single tap at " + point.toString(), Toast.LENGTH_SHORT).show();
	}

}
