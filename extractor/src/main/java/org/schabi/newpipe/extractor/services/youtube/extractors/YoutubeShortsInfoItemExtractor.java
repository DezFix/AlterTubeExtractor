package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.fixThumbnailUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getUrlFromNavigationEndpoint;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.Utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class YoutubeShortsInfoItemExtractor implements StreamInfoItemExtractor {
    private static final Pattern ACCESSIBILITY_TITLE_PATTERN = Pattern.compile(
            "^(.+), (?:[\\d,.]+(?:[KMB]| million| billion)?|No) views? - play Short$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{11}");

    private final JsonObject item;

    public YoutubeShortsInfoItemExtractor(@Nonnull final JsonObject item) {
        this.item = item;
    }

    @Override
    public String getName() throws ParsingException {
        final JsonObject primaryText = getObject(item, "overlayMetadata", "primaryText");
        String name = primaryText == null ? null : primaryText.getString("content");
        if (isNullOrEmpty(name)) {
            try {
                name = getTextFromObject(primaryText);
            } catch (final ParsingException ignored) {
            }
        }
        if (isNullOrEmpty(name)) {
            name = getString(item, "title", "content");
        }
        if (isNullOrEmpty(name)) {
            name = getString(item, "headline", "simpleText");
        }
        if (isNullOrEmpty(name)) {
            name = getString(item, "metadata", "lockupMetadataViewModel",
                    "title", "content");
        }
        if (isNullOrEmpty(name)) {
            final Matcher matcher = ACCESSIBILITY_TITLE_PATTERN.matcher(
                    item.getString("accessibilityText", ""));
            if (matcher.matches()) {
                name = matcher.group(1);
            }
        }
        if (isNullOrEmpty(name)) {
            throw new ParsingException("Could not get name");
        }
        return name;
    }

    @Override
    public String getUrl() throws ParsingException {
        final String videoId = getVideoId();
        if (isNullOrEmpty(videoId)) {
            throw new ParsingException("Could not get video ID");
        }

        try {
            return "https://www.youtube.com/shorts/" + videoId;
        } catch (final RuntimeException e) {
            throw new ParsingException("Could not get URL", e);
        }
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        String thumbnailUrl = getThumbnailUrl(getArray(item, "thumbnail", "sources"));
        if (isNullOrEmpty(thumbnailUrl)) {
            thumbnailUrl = getThumbnailUrl(getArray(item,
                    "thumbnailViewModel", "thumbnailViewModel", "image", "sources"));
        }
        if (isNullOrEmpty(thumbnailUrl)) {
            thumbnailUrl = getThumbnailUrl(getArray(item,
                    "thumbnailViewModel", "image", "sources"));
        }
        if (isNullOrEmpty(thumbnailUrl)) {
            thumbnailUrl = getThumbnailUrl(getArray(item, "thumbnailViewModel",
                    "image", "sources"));
        }
        if (isNullOrEmpty(thumbnailUrl)) {
            thumbnailUrl = getThumbnailUrl(getArray(item, "contentImage",
                    "thumbnailViewModel", "image", "sources"));
        }
        if (isNullOrEmpty(thumbnailUrl)) {
            thumbnailUrl = getThumbnailUrl(getArray(item, "onTap", "innertubeCommand",
                    "reelWatchEndpoint", "thumbnail", "thumbnails"));
        }
        if (isNullOrEmpty(thumbnailUrl)) {
            thumbnailUrl = getThumbnailUrl(getArray(item, "onTap", "innertubeCommand",
                    "reelWatchEndpoint", "thumbnail", "sources"));
        }
        if (isNullOrEmpty(thumbnailUrl)) {
            throw new ParsingException("Could not get thumbnail URL");
        }
        return thumbnailUrl;
    }

    @Override
    public StreamType getStreamType() {
        return hasLiveMarker(item) ? StreamType.LIVE_STREAM : StreamType.VIDEO_STREAM;
    }

    @Override
    public long getDuration() {
        return -1;
    }

    @Override
    public long getViewCount() {
        if (getStreamType() == StreamType.LIVE_STREAM) {
            return -1;
        }
        final String viewCountText = getString(item,
                "overlayMetadata", "secondaryText", "content");
        if (isNullOrEmpty(viewCountText) || viewCountText.contains("✪")) {
            return -1;
        }

        try {
            return Utils.mixedNumberWordToLong(viewCountText);
        } catch (final NumberFormatException | ParsingException e) {
            return -1;
        }
    }

    private static boolean hasLiveMarker(final JsonObject object) {
        return hasLiveMarker(object, 0);
    }

    private static boolean hasLiveMarker(final JsonObject object, final int depth) {
        if (object == null || depth > 12) {
            return false;
        }
        for (final java.util.Map.Entry<String, Object> entry : object.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            if ((key.equalsIgnoreCase("badgeStyle") || key.equalsIgnoreCase("style"))
                    && value instanceof String
                    && ((String) value).toUpperCase(java.util.Locale.ROOT).contains("LIVE")) {
                return true;
            }
            if (value instanceof JsonObject
                    && hasLiveMarker((JsonObject) value, depth + 1)) {
                return true;
            }
            if (value instanceof JsonArray
                    && hasLiveMarker((JsonArray) value, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLiveMarker(final JsonArray array, final int depth) {
        if (array == null || depth > 12) {
            return false;
        }
        for (final Object value : array) {
            if (value instanceof JsonObject
                    && hasLiveMarker((JsonObject) value, depth + 1)) {
                return true;
            }
            if (value instanceof JsonArray
                    && hasLiveMarker((JsonArray) value, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public String getUploaderName() {
        final JsonObject uploaderText = getUploaderText();
        return uploaderText == null ? null : uploaderText.getString("content");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        final JsonObject endpoint = getUploaderNavigationEndpoint();
        return endpoint == null ? "" : getUrlFromNavigationEndpoint(endpoint);
    }

    @Nullable
    @Override
    public String getTextualUploadDate() {
        return null;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() {
        return null;
    }

    @Override
    public boolean isShortFormContent() {
        return true;
    }

    @Nullable
    private JsonObject getUploaderText() {
        final JsonArray rows = getArray(item, "metadata", "lockupMetadataViewModel",
                "metadata", "contentMetadataViewModel", "metadataRows");
        if (rows == null) {
            return null;
        }
        for (final Object rowObject : rows) {
            if (!(rowObject instanceof JsonObject)) {
                continue;
            }
            final JsonArray parts = ((JsonObject) rowObject).getArray("metadataParts");
            if (parts == null) {
                continue;
            }
            for (final Object partObject : parts) {
                if (!(partObject instanceof JsonObject)) {
                    continue;
                }
                final JsonObject text = ((JsonObject) partObject).getObject("text");
                final String content = text.getString("content");
                if (!isNullOrEmpty(content)) {
                    return text;
                }
            }
        }
        return null;
    }

    @Nullable
    private JsonObject getUploaderNavigationEndpoint() {
        final JsonObject text = getUploaderText();
        if (text == null) {
            return null;
        }
        final JsonArray commandRuns = text.getArray("commandRuns");
        if (commandRuns == null || commandRuns.isEmpty()) {
            return null;
        }
        return commandRuns.getObject(0).getObject("onTap").getObject("innertubeCommand");
    }

    @Nullable
    private String getVideoId() {
        String videoId = getString(item, "onTap", "innertubeCommand",
                "reelWatchEndpoint", "videoId");
        if (isNullOrEmpty(videoId)) {
            videoId = getString(item, "onTap", "serviceEndpoint",
                    "reelWatchEndpoint", "videoId");
        }
        if (isNullOrEmpty(videoId)) {
            videoId = getString(item, "rendererContext", "commandContext", "onTap",
                    "innertubeCommand", "reelWatchEndpoint", "videoId");
        }
        if (isNullOrEmpty(videoId)) {
            videoId = getString(item, "inlinePlayerData", "onVisible", "innertubeCommand",
                    "watchEndpoint", "videoId");
        }
        if (isNullOrEmpty(videoId)) {
            videoId = getString(item, "onTap", "innertubeCommand",
                    "watchEndpoint", "videoId");
        }
        if (isNullOrEmpty(videoId)) {
            videoId = getString(item, "onTap", "serviceEndpoint",
                    "watchEndpoint", "videoId");
        }
        if (isNullOrEmpty(videoId)) {
            videoId = getString(item, "contentId");
        }
        if (isNullOrEmpty(videoId)) {
            videoId = getString(item, "videoId");
        }
        if (isNullOrEmpty(videoId)) {
            final String entityId = getString(item, "entityId");
            if (!isNullOrEmpty(entityId)) {
                if (entityId.startsWith("shorts-shelf-item-")) {
                    final String candidate = entityId.substring(
                            "shorts-shelf-item-".length());
                    if (VIDEO_ID_PATTERN.matcher(candidate).matches()) {
                        videoId = candidate;
                    }
                } else {
                    final Matcher matcher = VIDEO_ID_PATTERN.matcher(entityId);
                    if (matcher.find()) {
                        videoId = matcher.group();
                    }
                }
            }
        }
        return !isNullOrEmpty(videoId) && VIDEO_ID_PATTERN.matcher(videoId).matches()
                ? videoId : null;
    }

    @Nullable
    private static String getThumbnailUrl(@Nullable final JsonArray sources) {
        if (sources == null) {
            return null;
        }

        String bestUrl = null;
        long bestArea = -1;
        for (final Object sourceObject : sources) {
            if (!(sourceObject instanceof JsonObject)) {
                continue;
            }

            final JsonObject source = (JsonObject) sourceObject;
            final String url = source.getString("url");
            if (isNullOrEmpty(url)) {
                continue;
            }

            final int width = source.getInt("width", 0);
            final int height = source.getInt("height", 0);
            final long area = width > 0 && height > 0 ? (long) width * height : 0;
            if (area >= bestArea) {
                bestUrl = url;
                bestArea = area;
            }
        }

        return isNullOrEmpty(bestUrl) ? null : fixThumbnailUrl(bestUrl);
    }

    @Nullable
    private static String getString(@Nonnull final JsonObject object,
                                    @Nonnull final String... path) {
        if (path.length == 0) {
            return null;
        }

        try {
            JsonObject currentObject = object;
            for (int i = 0; i < path.length - 1; i++) {
                currentObject = currentObject.getObject(path[i]);
                if (currentObject == null) {
                    return null;
                }
            }
            return currentObject.getString(path[path.length - 1]);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static JsonObject getObject(@Nonnull final JsonObject object,
                                       @Nonnull final String... path) {
        if (path.length == 0) {
            return null;
        }

        try {
            JsonObject currentObject = object;
            for (final String key : path) {
                currentObject = currentObject.getObject(key);
                if (currentObject == null) {
                    return null;
                }
            }
            return currentObject;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static JsonArray getArray(@Nonnull final JsonObject object,
                                      @Nonnull final String... path) {
        if (path.length == 0) {
            return null;
        }

        try {
            JsonObject currentObject = object;
            for (int i = 0; i < path.length - 1; i++) {
                currentObject = currentObject.getObject(path[i]);
                if (currentObject == null) {
                    return null;
                }
            }
            return currentObject.getArray(path[path.length - 1]);
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
