package cz.mzk.tiledimageview.demonstration.kramerius;

import android.content.Intent;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import cz.mzk.tiledimageview.TiledImageView.ViewMode;
import cz.mzk.tiledimageview.demonstration.R;
import cz.mzk.tiledimageview.demonstration.kramerius.DownloadPageListTask.DownloadPidListResultHandler;
import cz.mzk.tiledimageview.demonstration.kramerius.IPageViewerFragment.EventListener;
import cz.mzk.tiledimageview.demonstration.kramerius.api.AltoParser;
import cz.mzk.tiledimageview.demonstration.kramerius.api.K5Connector;
import cz.mzk.tiledimageview.demonstration.kramerius.api.K5ConnectorImpl;
import cz.mzk.tiledimageview.rectangles.FramingRectangle;

/**
 * @author Martin Řehánek
 */
public class PageViewerActivity extends FragmentActivity implements EventListener {

    public static final String EXTRA_PROTOCOL = "protocol";
    public static final String EXTRA_DOMAIN = "domain";
    public static final String EXTRA_TOP_LEVEL_PID = "topLevelPid";
    public static final String EXTRA_PAGE_PIDS = "pagePids";
    private static final String TAG = PageViewerActivity.class.getSimpleName();
    private String mProtocol;
    private String mDomain;
    private String mTopLevelPid;
    private List<String> mPagePids;

    private View root;
    private View mViewProgressBar;
    private IPageViewerFragment mPageViewerFragment;
    private PageControlsFragment mControlFragment;
    private AsyncTask mSearchTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_page_viewer);
        root = findViewById(R.id.root);
        mViewProgressBar = findViewById(R.id.viewProgressBar);
        FragmentManager fragmentManager = getSupportFragmentManager();
        mPageViewerFragment = (IPageViewerFragment) fragmentManager.findFragmentById(R.id.fragmentViewer);
        mPageViewerFragment.setEventListener(this);
        mControlFragment = (PageControlsFragment) fragmentManager.findFragmentById(R.id.fragmentControls);
        if (savedInstanceState != null) {
            restoreOrLoadData(savedInstanceState);
        } else {
            restoreOrLoadData(getIntent().getExtras());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(EXTRA_PROTOCOL, mProtocol);
        outState.putString(EXTRA_DOMAIN, mDomain);
        outState.putString(EXTRA_TOP_LEVEL_PID, mTopLevelPid);
        if (mPagePids != null) {
            String[] pidsArray = new String[mPagePids.size()];
            outState.putStringArray(EXTRA_PAGE_PIDS, mPagePids.toArray(pidsArray));
        }
        super.onSaveInstanceState(outState);
    }

    private void restoreOrLoadData(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Log.d(TAG, "restoring data");
            mProtocol = savedInstanceState.getString(EXTRA_PROTOCOL);
            mDomain = savedInstanceState.getString(EXTRA_DOMAIN);
            mTopLevelPid = savedInstanceState.getString(EXTRA_TOP_LEVEL_PID);
            if (savedInstanceState.containsKey(EXTRA_PAGE_PIDS)) {
                mPagePids = Arrays.asList(savedInstanceState.getStringArray(EXTRA_PAGE_PIDS));
                initPageViewerFragment();
            } else {
                new DownloadPageListTask(PageViewerActivity.this, mProtocol, mDomain, mTopLevelPid,
                        new DownloadPidListResultHandler() {

                            @Override
                            public void onSuccess(List<String> pidList) {
                                mPagePids = pidList;
                                initPageViewerFragment();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                mViewProgressBar.setVisibility(View.INVISIBLE);
                                Toast.makeText(PageViewerActivity.this, "error getting pages: " + errorMessage,
                                        Toast.LENGTH_LONG).show();
                            }

                        }).executeConcurrentIfPossible();
            }
        } else {
            Log.d(TAG, "bundle is null");
        }
    }

    private void initPageViewerFragment() {
        Log.d(TAG, "initializing PageViewerFragment");
        mViewProgressBar.setVisibility(View.INVISIBLE);
        if (!mPageViewerFragment.isPopulated()) {
            mPageViewerFragment.populate(mProtocol, mDomain, mPagePids);
        } else {
            int currentPageIndex = mPageViewerFragment.getCurrentPageIndex();
            Log.d(TAG, "current page: " + currentPageIndex);
            showPage(currentPageIndex);
        }
    }

    @Override
    public void onReady() {
        Log.d(TAG, "PageViewerFragment ready");
        int currentPageIndex = mPageViewerFragment.getCurrentPageIndex();
        Log.d(TAG, "current page: " + currentPageIndex);
        showPage(currentPageIndex);
    }

    @Override
    public void onSingleTap(float x, float y, Rect boundingBox) {
        Log.d(TAG, "Showing metadata after single tap");
        int pageIndex = mPageViewerFragment.getCurrentPageIndex();
        String pagePid = mPageViewerFragment.getPagePid(pageIndex);
        Intent intent = new Intent(this, PageMetadataActivity.class);
        intent.putExtra(PageMetadataActivity.EXTRA_TOP_LEVEL_PID, mTopLevelPid);
        intent.putExtra(PageMetadataActivity.EXTRA_PAGE_PID, pagePid);
        intent.putExtra(PageMetadataActivity.EXTRA_PAGE_INDEX, pageIndex);
        intent.putExtra(PageMetadataActivity.EXTRA_BOUNDING_BOX_BOTTOM, boundingBox.bottom);
        intent.putExtra(PageMetadataActivity.EXTRA_BOUNDING_BOX_LEFT, boundingBox.left);
        intent.putExtra(PageMetadataActivity.EXTRA_BOUNDING_BOX_RIGHT, boundingBox.right);
        intent.putExtra(PageMetadataActivity.EXTRA_BOUNDING_BOX_TOP, boundingBox.top);
        startActivity(intent);
    }

    public void showNextPage() {
        int index = mPageViewerFragment.getCurrentPageIndex() + 1;
        Log.d(TAG, "showing next page (" + index + ")");
        showPage(index);
    }

    public void showPreviousPage() {
        int index = mPageViewerFragment.getCurrentPageIndex() - 1;
        Log.d(TAG, "showing previous page (" + index + ")");
        showPage(index);
    }

    private void showPage(int index) {
        mControlFragment.setBtnPreviousPageEnabled(index > 0);
        mControlFragment.setBtnNextPageEnabled(index < (mPageViewerFragment.getPageNumber() - 1));
        mControlFragment.setPageCounterContent(mPageViewerFragment.getPageNumber(), index + 1);
        mControlFragment.clearSearch();
        mPageViewerFragment.showPage(index);
        mPageViewerFragment.setFramingRectangles(null);
    }

    public void setViewMode(ViewMode mode) {
        mPageViewerFragment.setViewMode(mode);
        if (mPageViewerFragment.isPopulated()) {
            mPageViewerFragment.showPage(mPageViewerFragment.getCurrentPageIndex());
        }
    }

    public void searchWords(final String queryString) {
        if (mSearchTask != null && mSearchTask.getStatus() != AsyncTask.Status.FINISHED) {
            mSearchTask.cancel(false);
        }
        mSearchTask = new AsyncTask<String, Void, List<FramingRectangle>>() {

            @Override
            protected List<FramingRectangle> doInBackground(String... params) {
                K5Connector con = new K5ConnectorImpl();
                String currentPagePid = mPagePids.get(mPageViewerFragment.getCurrentPageIndex());
                Set<AltoParser.TextBox> blocks = con.getBoxes(mProtocol, mDomain, currentPagePid, params[0]);
                if (isCancelled()) {
                    return null;
                }
                List<FramingRectangle> rectangles = new ArrayList<>();
                for (AltoParser.TextBox block : blocks) {
                    rectangles.add(new FramingRectangle(block.getRectangle(), new FramingRectangle.Border(R.color.framing_rect_border, 1), R.color.framing_rect_filling));
                }
                return rectangles;
            }

            @Override
            protected void onPostExecute(List<FramingRectangle> framingRectangles) {
                if (framingRectangles != null) {
                    Log.d(TAG, "results for '" + queryString + "': " + framingRectangles.size());
                    mPageViewerFragment.setFramingRectangles(framingRectangles);
                }
                mSearchTask = null;
            }
        }.execute(queryString);
    }


    @Override
    public void onStop() {
        super.onStop();
        if (mSearchTask != null && mSearchTask.getStatus() != AsyncTask.Status.FINISHED) {
            mSearchTask.cancel(false);
        }
    }

}
