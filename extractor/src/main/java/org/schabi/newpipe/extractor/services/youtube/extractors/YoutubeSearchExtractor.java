package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.DISABLE_PRETTY_PRINT_PARAMETER;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.YOUTUBEI_V1_URL;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getValidJsonResponseBody;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;
import static org.schabi.newpipe.extractor.utils.Utils.UTF_8;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeFilters;
import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonBuilder;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nonnull;

public class YoutubeSearchExtractor extends YoutubeBaseSearchExtractor {
    private JsonObject initialData;

    public YoutubeSearchExtractor(final StreamingService service,
                                  final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) throws IOException,
            ExtractionException {
        final String query = super.getSearchString();
        final Localization localization = getExtractorLocalization();

        // Get the search parameter for the request
        final YoutubeFilters.YoutubeContentFilterItem contentFilterItem =
                getSelectedContentFilterItem();
        final String params = contentFilterItem.getParams();

        final JsonBuilder<JsonObject> jsonBody = prepareDesktopJsonBuilder(localization,
                getExtractorContentCountry())
                .value("query", query);
        if (!isNullOrEmpty(params)) {
            jsonBody.value("params", params);
        }

        final byte[] body = JsonWriter.string(jsonBody.done()).getBytes(UTF_8);

        initialData = getJsonPostResponse("search", body, localization);
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        return super.getUrl() + "&gl=" + getExtractorContentCountry().getCountryCode();
    }

    @Nonnull
    @Override
    public String getSearchSuggestion() throws ParsingException {
        final JsonObject itemSectionRenderer = initialData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents").getObject(0)
                .getObject("itemSectionRenderer");
        final JsonObject didYouMeanRenderer = itemSectionRenderer.getArray("contents").getObject(0)
                .getObject("didYouMeanRenderer");
        final JsonObject showingResultsForRenderer = itemSectionRenderer.getArray("contents")
                .getObject(0)
                .getObject("showingResultsForRenderer");

        if (!didYouMeanRenderer.isEmpty()) {
            return JsonUtils.getString(didYouMeanRenderer,
                    "correctedQueryEndpoint.searchEndpoint.query");
        } else if (showingResultsForRenderer != null) {
            return getTextFromObject(showingResultsForRenderer.getObject("correctedQuery"));
        } else {
            return "";
        }
    }

    @Override
    public boolean isCorrectedSearch() {
        final JsonObject showingResultsForRenderer = initialData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents").getObject(0)
                .getObject("itemSectionRenderer").getArray("contents").getObject(0)
                .getObject("showingResultsForRenderer");
        return !showingResultsForRenderer.isEmpty();
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return YoutubeParsingHelper.getMetaInfo(
                initialData.getObject("contents").getObject("twoColumnSearchResultsRenderer")
                        .getObject("primaryContents").getObject("sectionListRenderer")
                        .getArray("contents"));
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException {
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

        final JsonArray sections = initialData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents");

        Page nextPage = null;

        for (final Object section : sections) {
            if (((JsonObject) section).has("itemSectionRenderer")) {
                final JsonObject itemSectionRenderer = ((JsonObject) section)
                        .getObject("itemSectionRenderer");

                collectStreamsFrom(collector, itemSectionRenderer.getArray("contents"));
            } else if (((JsonObject) section).has("continuationItemRenderer")) {
                nextPage = getNextPageFrom(((JsonObject) section)
                        .getObject("continuationItemRenderer"));
            }
        }
        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public InfoItemsPage<InfoItem> getPageInternal(final Page page) throws IOException,
            ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final Localization localization = getExtractorLocalization();
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

        // @formatter:off
        final byte[] json = JsonWriter.string(prepareDesktopJsonBuilder(localization,
                getExtractorContentCountry())
                .value("continuation", page.getId())
                .done())
                .getBytes(UTF_8);
        // @formatter:on

        final String responseBody = getValidJsonResponseBody(getDownloader().post(
                page.getUrl(), new HashMap<>(), json));

        final JsonObject ajaxJson;
        try {
            ajaxJson = JsonParser.object().from(responseBody);
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse JSON", e);
        }

        final List<JsonArray> continuationGroups = new ArrayList<>();
        collectContinuationGroups(ajaxJson.getArray("onResponseReceivedCommands"),
                continuationGroups);
        collectContinuationGroups(ajaxJson.getArray("onResponseReceivedActions"),
                continuationGroups);
        Page nextPage = null;
        final TimeAgoParser timeAgoParser = getTimeAgoParser();
        for (final JsonArray continuationItems : continuationGroups) {
            for (final Object continuationObject : continuationItems) {
                if (!(continuationObject instanceof JsonObject)) {
                    continue;
                }
                final JsonObject continuation = (JsonObject) continuationObject;
                if (continuation.has("continuationItemRenderer")) {
                    if (nextPage == null) {
                        nextPage = getNextPageFrom(
                                continuation.getObject("continuationItemRenderer"));
                    }
                } else {
                    collectStreamItem(collector, continuation, timeAgoParser);
                }
            }
        }
        return new InfoItemsPage<>(collector, nextPage);
    }

    private void collectContinuationGroups(final JsonArray commands,
                                           final List<JsonArray> groups) {
        if (commands == null) {
            return;
        }
        for (final Object commandObject : commands) {
            if (!(commandObject instanceof JsonObject)) {
                continue;
            }
            final JsonObject command = (JsonObject) commandObject;
            if (command.has("appendContinuationItemsAction")) {
                groups.add(command.getObject("appendContinuationItemsAction")
                        .getArray("continuationItems"));
            } else if (command.has("reloadContinuationItemsCommand")) {
                groups.add(command.getObject("reloadContinuationItemsCommand")
                        .getArray("continuationItems"));
            }
        }
    }

    private void collectStreamsFrom(final MultiInfoItemsCollector collector,
                                    final JsonArray contents) throws NothingFoundException,
            ParsingException {
        if (contents == null) {
            return;
        }
        final TimeAgoParser timeAgoParser = getTimeAgoParser();
        for (final Object content : contents) {
            if (content instanceof JsonObject) {
                collectStreamItem(collector, (JsonObject) content, timeAgoParser);
            }
        }
    }

    private void collectStreamItem(final MultiInfoItemsCollector collector,
                                   final JsonObject item,
                                   final TimeAgoParser timeAgoParser)
            throws NothingFoundException, ParsingException {
        if (item.has("backgroundPromoRenderer")) {
            throw new NothingFoundException(getTextFromObject(
                    item.getObject("backgroundPromoRenderer").getObject("bodyText")));
        } else if (item.has("videoRenderer")) {
            collector.commit(new YoutubeStreamInfoItemExtractor(
                    item.getObject("videoRenderer"), timeAgoParser));
        } else if (item.has("reelItemRenderer")) {
            collector.commit(new YoutubeStreamInfoItemExtractor(
                    item.getObject("reelItemRenderer"), timeAgoParser));
        } else if (item.has("shortsLockupViewModel")) {
            collector.commit(new YoutubeShortsInfoItemExtractor(
                    item.getObject("shortsLockupViewModel")));
        } else if (item.has("shortsLockupViewModelV2")) {
            collector.commit(new YoutubeShortsInfoItemExtractor(
                    item.getObject("shortsLockupViewModelV2")));
        } else if (item.has("channelRenderer")) {
            collector.commit(new YoutubeChannelInfoItemExtractor(
                    item.getObject("channelRenderer")));
        } else if (item.has("playlistRenderer")) {
            collector.commit(new YoutubePlaylistInfoItemExtractor(
                    item.getObject("playlistRenderer")));
        } else if (item.has("lockupViewModel")) {
            final JsonObject lockupViewModel = item.getObject("lockupViewModel");
            if ("LOCKUP_CONTENT_TYPE_PLAYLIST".equals(
                    lockupViewModel.getString("contentType"))) {
                collector.commit(
                        new YoutubeMixOrPlaylistLockupInfoItemExtractor(lockupViewModel));
            }
        } else if (item.has("richItemRenderer")) {
            collectStreamItem(collector,
                    item.getObject("richItemRenderer").getObject("content"), timeAgoParser);
        } else if (item.has("gridShelfViewModel")) {
            final JsonObject shelf = item.getObject("gridShelfViewModel");
            final JsonArray shelfContents = shelf.getArray("contents");
            collectStreamsFrom(collector,
                    shelfContents == null || shelfContents.isEmpty()
                            ? shelf.getArray("items") : shelfContents);
        } else if (item.has("reelShelfRenderer")) {
            collectStreamsFrom(collector,
                    item.getObject("reelShelfRenderer").getArray("items"));
        } else if (item.has("itemSectionRenderer")) {
            collectStreamsFrom(collector,
                    item.getObject("itemSectionRenderer").getArray("contents"));
        }
    }

    private Page getNextPageFrom(final JsonObject continuationItemRenderer) throws IOException,
            ExtractionException {
        if (isNullOrEmpty(continuationItemRenderer)) {
            return null;
        }

        final String token = continuationItemRenderer.getObject("continuationEndpoint")
                .getObject("continuationCommand").getString("token");

        final String url = YOUTUBEI_V1_URL + "search?"
                + DISABLE_PRETTY_PRINT_PARAMETER;

        return new Page(url, token);
    }
}
