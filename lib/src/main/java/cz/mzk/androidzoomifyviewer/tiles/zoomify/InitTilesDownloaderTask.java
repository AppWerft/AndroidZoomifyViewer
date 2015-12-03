package cz.mzk.androidzoomifyviewer.tiles.zoomify;

import cz.mzk.androidzoomifyviewer.ConcurrentAsyncTask;
import cz.mzk.androidzoomifyviewer.Logger;
import cz.mzk.androidzoomifyviewer.tiles.TilesDownloader;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.ImageServerResponseException;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.InvalidDataException;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.OtherIOException;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.TooManyRedirectionsException;

/**
 * @author Martin Řehánek
 */
public class InitTilesDownloaderTask extends ConcurrentAsyncTask<Void, Void, TilesDownloader> {

    private static final Logger logger = new Logger(InitTilesDownloaderTask.class);

    private final String zoomifyBaseUrl;
    private final double pxRatio;
    private final ImagePropertiesDownloadResultHandler handler;

    private OtherIOException otherIoException;
    private TooManyRedirectionsException tooManyRedirectionsException;
    private ImageServerResponseException imageServerResponseException;
    private InvalidDataException invalidXmlException;

    /**
     * @param zoomifyBaseUrl Zoomify base url, not null
     * @param pxRatio
     * @param handler        ImageProperties.xml download result handler, not null
     */
    public InitTilesDownloaderTask(String zoomifyBaseUrl, double pxRatio, ImagePropertiesDownloadResultHandler handler) {
        this.zoomifyBaseUrl = zoomifyBaseUrl;
        this.handler = handler;
        this.pxRatio = pxRatio;
    }

    @Override
    protected TilesDownloader doInBackground(Void... params) {
        try {
            //logger.d("downloading metadata from '" + zoomifyBaseUrl + "'");
            TilesDownloader downloader = new ZoomifyTilesDownloader(zoomifyBaseUrl, pxRatio);
            if (!isCancelled()) {
                downloader.initializeWithImageProperties();
                return downloader;
            }
        } catch (TooManyRedirectionsException e) {
            tooManyRedirectionsException = e;
        } catch (ImageServerResponseException e) {
            imageServerResponseException = e;
        } catch (InvalidDataException e) {
            invalidXmlException = e;
        } catch (OtherIOException e) {
            otherIoException = e;
        }
        return null;
    }

    @Override
    protected void onPostExecute(TilesDownloader downloader) {
        if (tooManyRedirectionsException != null) {
            handler.onRedirectionLoop(tooManyRedirectionsException.getUrl(),
                    tooManyRedirectionsException.getRedirections());
        } else if (imageServerResponseException != null) {
            handler.onUnhandableResponseCode(imageServerResponseException.getUrl(),
                    imageServerResponseException.getErrorCode());
        } else if (invalidXmlException != null) {
            handler.onInvalidData(invalidXmlException.getUrl(), invalidXmlException.getMessage());
        } else if (otherIoException != null) {
            handler.onDataTransferError(otherIoException.getUrl(), otherIoException.getMessage());
        } else {
            handler.onSuccess(downloader);
        }
    }

    public interface ImagePropertiesDownloadResultHandler {

        public void onSuccess(TilesDownloader downloader);

        public void onUnhandableResponseCode(String imagePropertiesUrl, int responseCode);

        public void onRedirectionLoop(String imagePropertiesUrl, int redirections);

        public void onDataTransferError(String imagePropertiesUrl, String errorMessage);

        public void onInvalidData(String imagePropertiesUrl, String errorMessage);

    }

}
