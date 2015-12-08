package cz.mzk.androidzoomifyviewer.tiles.metadata;

import cz.mzk.androidzoomifyviewer.tiles.exceptions.InvalidDataException;
import cz.mzk.androidzoomifyviewer.tiles.exceptions.OtherIOException;

/**
 * Created by Martin Řehánek on 8.12.15.
 */
public interface ImageMetadataParser {

    public ImageMetadata parse(String metadataStr, String metadataUrl) throws InvalidDataException, OtherIOException;
}
