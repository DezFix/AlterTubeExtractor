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

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class YoutubeShortsInfoItemExtractor implements StreamInfoItemExtractor {
    private static final Pattern ACCESSIBILITY_TITLE_PATTERN = Pattern.compile(
            "^(.+), (?:[\\d,.]+(?:[KMB]| million| billion)?|No) views? - play Short$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "[\\d]+(?:[\\s\\u00A0][\\d]+)*(?:[\\.,][\\d]+)?");
    private static final Pattern COMPACT_MULTIPLIER_PATTERN = Pattern.compile(
            "([\\d]+(?:[\\.,][\\d]+)?)([KMBkmb])");

    private final JsonObject item;

    public YoutubeShortsInfoItemExtractor(@Nonnull final JsonObject item) {
        this.item = Objects.requireNonNull(item, "item");
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
            name = getNameFromAccessibilityText(item.getString("accessibilityText", ""));
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
        if (getStreamType() == StreamType.LIVE_STREAM || requiresMembership()) {
            return -1;
        }
        final String viewCountText = getString(item,
                "overlayMetadata", "secondaryText", "content");
        if (isNullOrEmpty(viewCountText) || viewCountText.contains("✪")) {
            return -1;
        }

        try {
            final Long viewCount = parseViewCountText(viewCountText);
            return viewCount == null ? -1 : viewCount;
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
        if (uploaderText != null) {
            final String uploaderName = uploaderText.getString("content");
            if (!isNullOrEmpty(uploaderName)) {
                return uploaderName;
            }
        }
        return getFirstContributorName(getUploaderNavigationEndpoint());
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        final JsonObject endpoint = getUploaderNavigationEndpoint();
        if (endpoint == null || endpoint.isEmpty()) {
            return "";
        }
        try {
            final String uploaderUrl = getUrlFromNavigationEndpoint(endpoint);
            return isNullOrEmpty(uploaderUrl) ? "" : uploaderUrl;
        } catch (final Exception ignored) {
            return "";
        }
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

    @Override
    public boolean requiresMembership() {
        return hasMembershipMarker(item);
    }

    private static boolean hasMembershipMarker(final JsonObject object) {
        return hasMembershipMarker(object, 0);
    }

    private static boolean hasMembershipMarker(final JsonObject object, final int depth) {
        if (object == null || depth > 12) {
            return false;
        }
        for (final java.util.Map.Entry<String, Object> entry : object.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            if ((key.equalsIgnoreCase("badgeStyle") || key.equalsIgnoreCase("style"))
                    && value instanceof String
                    && isMembershipStyle((String) value)) {
                return true;
            }
            if ((key.equalsIgnoreCase("content") || key.equalsIgnoreCase("text"))
                    && value instanceof String
                    && ((String) value).contains("✪")) {
                return true;
            }
            if (value instanceof JsonObject
                    && hasMembershipMarker((JsonObject) value, depth + 1)) {
                return true;
            }
            if (value instanceof JsonArray
                    && hasMembershipMarker((JsonArray) value, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMembershipMarker(final JsonArray array, final int depth) {
        if (array == null || depth > 12) {
            return false;
        }
        for (final Object value : array) {
            if (value instanceof JsonObject
                    && hasMembershipMarker((JsonObject) value, depth + 1)) {
                return true;
            }
            if (value instanceof JsonArray
                    && hasMembershipMarker((JsonArray) value, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMembershipStyle(final String value) {
        final String normalized = value.toUpperCase(Locale.ROOT);
        return normalized.contains("MEMBERS_ONLY")
                || normalized.contains("MEMBER_ONLY")
                || normalized.equals("MEMBERS")
                || normalized.equals("MEMBER");
    }

    @Nullable
    private JsonObject getUploaderText() {
        final JsonArray rows = getArray(item, "metadata", "lockupMetadataViewModel",
                "metadata", "contentMetadataViewModel", "metadataRows");
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        JsonObject fallback = null;
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
                final JsonObject metadataPart = (JsonObject) partObject;
                final JsonObject text = metadataPart.getObject("text");
                if (text == null || text.isEmpty()) {
                    continue;
                }
                final String content = text.getString("content");
                if (isNullOrEmpty(content)) {
                    continue;
                }
                if (hasChannelEndpoint(text)) {
                    return text;
                }
                if (isViewCountText(content) || isViewCountPart(metadataPart)
                        || isUploadDateText(content)) {
                    continue;
                }
                if (fallback == null) {
                    fallback = text;
                }
            }
        }
        return fallback;
    }

    @Nullable
    private JsonObject getUploaderNavigationEndpoint() {
        final JsonObject text = getUploaderText();
        if (text != null) {
            final JsonArray commandRuns = text.getArray("commandRuns");
            if (commandRuns != null) {
                for (final Object commandRunObject : commandRuns) {
                    if (!(commandRunObject instanceof JsonObject)) {
                        continue;
                    }
                    final JsonObject endpoint = getObject((JsonObject) commandRunObject,
                            "onTap", "innertubeCommand");
                    if (endpoint != null && !endpoint.isEmpty()) {
                        return endpoint;
                    }
                }
            }
            final JsonObject navigationEndpoint = text.getObject("navigationEndpoint");
            if (navigationEndpoint != null && !navigationEndpoint.isEmpty()) {
                return navigationEndpoint;
            }
        }

        final JsonObject image = getObject(item, "metadata", "lockupMetadataViewModel", "image");
        if (image != null && !image.isEmpty()) {
            final JsonObject decoratedAvatar = image.getObject("decoratedAvatarViewModel");
            if (decoratedAvatar != null && !decoratedAvatar.isEmpty()) {
                final JsonObject endpoint = getObject(decoratedAvatar,
                        "rendererContext", "commandContext", "onTap", "innertubeCommand");
                if (endpoint != null && !endpoint.isEmpty()) {
                    return endpoint;
                }
            }
            final JsonObject avatarStack = image.getObject("avatarStackViewModel");
            if (avatarStack != null && !avatarStack.isEmpty()) {
                final JsonObject endpoint = getObject(avatarStack,
                        "rendererContext", "commandContext", "onTap", "innertubeCommand");
                if (endpoint != null && !endpoint.isEmpty()) {
                    return endpoint;
                }
            }
        }
        return null;
    }

    private String getFirstContributorName(final JsonObject endpoint) {
        if (endpoint == null || !endpoint.has("showDialogCommand")) {
            return null;
        }
        try {
            final JsonArray listItems = endpoint.getObject("showDialogCommand")
                    .getObject("panelLoadingStrategy").getObject("inlineContent")
                    .getObject("dialogViewModel").getObject("customContent")
                    .getObject("listViewModel").getArray("listItems");
            if (listItems == null || listItems.isEmpty()) {
                return null;
            }
            return listItems.getObject(0).getObject("listItemViewModel")
                    .getObject("title").getString("content", "");
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static boolean hasChannelEndpoint(final JsonObject text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        final JsonArray commandRuns = text.getArray("commandRuns");
        if (commandRuns != null) {
            for (final Object commandRunObject : commandRuns) {
                if (!(commandRunObject instanceof JsonObject)) {
                    continue;
                }
                final JsonObject endpoint = getObject((JsonObject) commandRunObject,
                        "onTap", "innertubeCommand");
                if (endpoint != null && !endpoint.isEmpty()) {
                    return true;
                }
            }
        }
        final JsonObject navigationEndpoint = text.getObject("navigationEndpoint");
        return navigationEndpoint != null && !navigationEndpoint.isEmpty();
    }

    private static boolean isViewCountText(final String text) {
        if (isNullOrEmpty(text)) {
            return false;
        }
        final String lowerCaseText = text.toLowerCase(Locale.ROOT);
        return lowerCaseText.matches(".*\\bviews?\\b.*")
                || lowerCaseText.contains("ukubukwa")
                || lowerCaseText.contains("no views")
                || lowerCaseText.contains("akukho")
                || lowerCaseText.contains("просмотр")
                || lowerCaseText.contains("перегляд")
                || lowerCaseText.contains("visualiz")
                || lowerCaseText.contains("vues")
                || lowerCaseText.contains("aufr")
                || lowerCaseText.contains("观看")
                || lowerCaseText.contains("再生");
    }

    private static boolean isViewCountPart(final JsonObject metadataPart) {
        if (isViewCountText(metadataPart.getString("accessibilityLabel"))) {
            return true;
        }
        final JsonObject leadingIcon = metadataPart.getObject("leadingIcon");
        return !leadingIcon.isEmpty()
                && "PLAY_ARROW_OUTLINED".equals(leadingIcon.getString("name"));
    }

    private static boolean isUploadDateText(final String text) {
        if (isNullOrEmpty(text)) {
            return false;
        }
        final String lowerCaseText = text.toLowerCase(Locale.ROOT);
        return lowerCaseText.matches(".*\\b(ago|yesterday|today|watched|streamed)\\b.*")
                || lowerCaseText.matches(".*\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b.*");
    }

    @Nullable
    private static String getNameFromAccessibilityText(final String text) {
        if (isNullOrEmpty(text)) {
            return null;
        }
        final Matcher matcher = ACCESSIBILITY_TITLE_PATTERN.matcher(text);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        final int separator = text.lastIndexOf(',');
        if (separator <= 0) {
            return null;
        }
        final String suffix = text.substring(separator + 1).trim();
        final String lowerCaseSuffix = suffix.toLowerCase(Locale.ROOT);
        final boolean hasNumber = suffix.matches(".*\\d.*");
        final boolean hasViewLabel = lowerCaseSuffix.contains("view")
                || lowerCaseSuffix.contains("перегляд")
                || lowerCaseSuffix.contains("просмотр")
                || lowerCaseSuffix.contains("visualiz")
                || lowerCaseSuffix.contains("vues")
                || lowerCaseSuffix.contains("aufr")
                || lowerCaseSuffix.contains("观看")
                || lowerCaseSuffix.contains("再生");
        final boolean hasCompactMultiplier = lowerCaseSuffix.matches(
                ".*\\d[\\d\\s.,]*[kmb](?:\\s|$).*");
        if (!hasNumber || (!hasViewLabel && !hasCompactMultiplier)) {
            return null;
        }
        final String name = text.substring(0, separator).trim();
        return isNullOrEmpty(name) ? null : name;
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
    private static Long parseViewCountText(@Nonnull final String text)
            throws ParsingException {
        final String lowerCaseText = text.toLowerCase(Locale.ROOT);
        if (lowerCaseText.contains("no views")
                || lowerCaseText.contains("akukho ukubukwa")
                || lowerCaseText.contains("akukho kubukwa")) {
            return 0L;
        }

        final Matcher compactMatcher = COMPACT_MULTIPLIER_PATTERN.matcher(text);
        if (compactMatcher.find()) {
            final long multiplier = getCompactMultiplier(compactMatcher.group(2).charAt(0));
            return scale(parseNumber(compactMatcher.group(1)), multiplier);
        }

        final long textualMultiplier = getTextualMultiplier(lowerCaseText);
        if (textualMultiplier > 1) {
            final String normalizedText = text.replaceAll(
                    "(?<=\\d)[\\s\\u00A0]+(?=\\d)", "");
            final Matcher numberMatcher = NUMBER_PATTERN.matcher(normalizedText);
            if (numberMatcher.find()) {
                return scale(parseNumber(numberMatcher.group()), textualMultiplier);
            }
        }

        if (!isViewCountText(text)) {
            return null;
        }
        final Matcher numberMatcher = NUMBER_PATTERN.matcher(text);
        if (numberMatcher.find()) {
            return scale(parseNumber(numberMatcher.group()), 1L);
        }
        return null;
    }

    private static long getCompactMultiplier(final char suffix) {
        switch (Character.toUpperCase(suffix)) {
            case 'K':
                return 1_000L;
            case 'M':
                return 1_000_000L;
            case 'B':
                return 1_000_000_000L;
            default:
                return 1L;
        }
    }

    private static long getTextualMultiplier(final String text) {
        if (text.contains("billion") || text.contains("milliard")
                || text.contains("млрд") || text.contains("мільярд")) {
            return 1_000_000_000L;
        }
        if (text.contains("million") || text.contains("milion")
                || text.contains("millon") || text.contains("млн")
                || text.contains("миллион") || text.contains("мільйон")) {
            return 1_000_000L;
        }
        if (text.contains("thousand") || text.contains("тыс")
                || text.contains("тис")) {
            return 1_000L;
        }
        return 1L;
    }

    private static long scale(final double value, final long multiplier) {
        return (long) (value * multiplier);
    }

    private static double parseNumber(final String value) {
        final String normalized = value.replace(" ", "").replace("\u00A0", "");
        final int commaIndex = normalized.lastIndexOf(',');
        final int dotIndex = normalized.lastIndexOf('.');
        if (commaIndex >= 0 && dotIndex >= 0) {
            final int decimalIndex = Math.max(commaIndex, dotIndex);
            final String integerPart = normalized.substring(0, decimalIndex)
                    .replace(",", "").replace(".", "");
            final String fractionPart = normalized.substring(decimalIndex + 1)
                    .replace(",", "").replace(".", "");
            return Double.parseDouble(integerPart + "." + fractionPart);
        }
        if (commaIndex >= 0) {
            if (normalized.length() - commaIndex - 1 == 3) {
                return Double.parseDouble(normalized.replace(",", ""));
            }
            return Double.parseDouble(normalized.replace(',', '.'));
        }
        if (dotIndex >= 0 && normalized.length() - dotIndex - 1 == 3) {
            return Double.parseDouble(normalized.replace(".", ""));
        }
        return Double.parseDouble(normalized);
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
        if (object == null || path.length == 0) {
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
        if (object == null || path.length == 0) {
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
        if (object == null || path.length == 0) {
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
