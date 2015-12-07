package cz.mzk.androidzoomifyviewer.tiles;

import cz.mzk.androidzoomifyviewer.ConcurrentAsyncTask;
import cz.mzk.androidzoomifyviewer.Logger;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.ImageServerResponseException;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.InvalidDataException;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.OtherIOException;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.TooManyRedirectionsException;

/**
 * @author Martin Řehánek
 */
public class InitImageManagerTask extends ConcurrentAsyncTask<Void, Void, Void> {

    private static final Logger logger = new Logger(InitImageManagerTask.class);
    private final MetadataInitializationHandler mHandler;
    private ImageManager mImgManager;
    private OtherIOException mOtherIoException;
    private TooManyRedirectionsException mTooManyRedirectionsException;
    private ImageServerResponseException mImageServerResponseException;
    private InvalidDataException mInvalidXmlException;

    public InitImageManagerTask(ImageManager imgManager, MetadataInitializationHandler handler) {
        mImgManager = imgManager;
        mHandler = handler;
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            //logger.d("downloading metadata from '" + zoomifyBaseUrl + "'");
            if (!isCancelled()) {
                mImgManager.initImageMetadata();
            }
        } catch (TooManyRedirectionsException e) {
            mTooManyRedirectionsException = e;
        } catch (ImageServerResponseException e) {
            mImageServerResponseException = e;
        } catch (InvalidDataException e) {
            mInvalidXmlException = e;
        } catch (OtherIOException e) {
            mOtherIoException = e;
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        if (mTooManyRedirectionsException != null) {
            mHandler.onRedirectionLoop(mTooManyRedirectionsException.getUrl(), mTooManyRedirectionsException.getRedirections());
        } else if (mImageServerResponseException != null) {
            mHandler.onUnhandableResponseCode(mImageServerResponseException.getUrl(), mImageServerResponseException.getErrorCode());
        } else if (mInvalidXmlException != null) {
            mHandler.onInvalidData(mInvalidXmlException.getUrl(), mInvalidXmlException.getMessage());
        } else if (mOtherIoException != null) {
            mHandler.onDataTransferError(mOtherIoException.getUrl(), mOtherIoException.getMessage());
        } else {
            mHandler.onSuccess(mImgManager);
        }
    }

}
