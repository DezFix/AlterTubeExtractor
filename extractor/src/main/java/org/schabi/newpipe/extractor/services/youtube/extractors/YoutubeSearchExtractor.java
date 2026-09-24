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
import javax.annotation.Nullable;

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
        if (initialData == null) {
            return "";
        }
        final JsonArray sections = initialData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents");
        if (sections == null || sections.isEmpty()) {
            return "";
        }
        final Object firstSectionObject = sections.get(0);
        if (!(firstSectionObject instanceof JsonObject)) {
            return "";
        }
        final JsonObject firstSection = (JsonObject) firstSectionObject;
        if (firstSection.isEmpty()) {
            return "";
        }
        final JsonObject itemSectionRenderer = firstSection.getObject("itemSectionRenderer");
        if (itemSectionRenderer == null || itemSectionRenderer.isEmpty()) {
            return "";
        }
        final JsonArray contents = itemSectionRenderer.getArray("contents");
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        final Object firstContentObject = contents.get(0);
        if (!(firstContentObject instanceof JsonObject)) {
            return "";
        }
        final JsonObject firstContent = (JsonObject) firstContentObject;
        if (firstContent.isEmpty()) {
            return "";
        }
        final JsonObject didYouMeanRenderer = firstContent.getObject("didYouMeanRenderer");
        if (didYouMeanRenderer != null && !didYouMeanRenderer.isEmpty()) {
            try {
                final String query = JsonUtils.getString(didYouMeanRenderer,
                        "correctedQueryEndpoint.searchEndpoint.query");
                return isNullOrEmpty(query) ? "" : query;
            } catch (final ParsingException ignored) {
                return "";
            }
        }
        final JsonObject showingResultsForRenderer = firstContent
                .getObject("showingResultsForRenderer");
        if (showingResultsForRenderer != null && !showingResultsForRenderer.isEmpty()) {
            final String query = getTextFromObject(
                    showingResultsForRenderer.getObject("correctedQuery"));
            return isNullOrEmpty(query) ? "" : query;
        }
        return "";
    }

    @Override
    public boolean isCorrectedSearch() {
        if (initialData == null) {
            return false;
        }
        final JsonArray sections = initialData.getObject("contents")
                .getObject("twoColumnSearchResultsRenderer").getObject("primaryContents")
                .getObject("sectionListRenderer").getArray("contents");
        if (sections == null || sections.isEmpty()) {
            return false;
        }
        final Object firstSectionObject = sections.get(0);
        if (!(firstSectionObject instanceof JsonObject)) {
            return false;
        }
        final JsonObject firstSection = (JsonObject) firstSectionObject;
        if (firstSection.isEmpty()) {
            return false;
        }
        final JsonObject itemSection = firstSection.getObject("itemSectionRenderer");
        if (itemSection == null || itemSection.isEmpty()) {
            return false;
        }
        final JsonArray contents = itemSection.getArray("contents");
        if (contents == null || contents.isEmpty()) {
            return false;
        }
        final Object firstContentObject = contents.get(0);
        if (!(firstContentObject instanceof JsonObject)) {
            return false;
        }
        final JsonObject firstContent = (JsonObject) firstContentObject;
        if (firstContent.isEmpty()) {
            return false;
        }
        final JsonObject showingResultsForRenderer = firstContent
                .getObject("showingResultsForRenderer");
        return showingResultsForRenderer != null && !showingResultsForRenderer.isEmpty();
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
        final TimeAgoParser timeAgoParser = getTimeAgoParser();
        Page nextPage = null;

        for (final Object section : sections) {
            if (!(section instanceof JsonObject)) {
                continue;
            }
            final Page sectionPage = collectStreamItem(collector,
                    (JsonObject) section, timeAgoParser);
            if (nextPage == null) {
                nextPage = sectionPage;
            }
        }
        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public InfoItemsPage<InfoItem> getPageInternal(final Page page) throws IOException,
            ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl()) || isNullOrEmpty(page.getId())) {
            throw new IllegalArgumentException("Page doesn't contain a valid continuation");
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
        collectContinuationGroups(ajaxJson, continuationGroups);
        final TimeAgoParser timeAgoParser = getTimeAgoParser();
        Page nextPage = null;
        for (final JsonArray continuationItems : continuationGroups) {
            final Page groupPage = collectStreamsFrom(collector, continuationItems,
                    timeAgoParser);
            if (nextPage == null) {
                nextPage = groupPage;
            }
        }
        return new InfoItemsPage<>(collector, nextPage);
    }

    private void collectContinuationGroups(final Object value,
                                           final List<JsonArray> groups) {
        if (value instanceof JsonObject) {
            final JsonObject object = (JsonObject) value;
            addContinuationGroup(object, "appendContinuationItemsAction", groups);
            addContinuationGroup(object, "reloadContinuationItemsCommand", groups);
            for (final Object nestedValue : object.values()) {
                collectContinuationGroups(nestedValue, groups);
            }
        } else if (value instanceof JsonArray) {
            for (final Object nestedValue : (JsonArray) value) {
                collectContinuationGroups(nestedValue, groups);
            }
        }
    }

    private void addContinuationGroup(final JsonObject object, final String name,
                                       final List<JsonArray> groups) {
        if (object == null) {
            return;
        }
        final JsonObject action = object.getObject(name);
        if (action == null || action.isEmpty()) {
            return;
        }
        final JsonArray continuationItems = action.getArray("continuationItems");
        if (continuationItems != null && !continuationItems.isEmpty()) {
            groups.add(continuationItems);
        }
    }

    @Nullable
    private Page collectStreamsFrom(final MultiInfoItemsCollector collector,
                                    final JsonArray contents,
                                    final TimeAgoParser timeAgoParser)
            throws NothingFoundException, ParsingException {
        if (contents == null || contents.isEmpty()) {
            return null;
        }
        Page nextPage = null;
        for (final Object content : contents) {
            if (!(content instanceof JsonObject)) {
                continue;
            }
            final Page page = collectStreamItem(collector, (JsonObject) content,
                    timeAgoParser);
            if (nextPage == null) {
                nextPage = page;
            }
        }
        return nextPage;
    }

    @Nullable
    private Page collectStreamItem(final MultiInfoItemsCollector collector,
                                   final JsonObject item,
                                   final TimeAgoParser timeAgoParser)
            throws NothingFoundException, ParsingException {
        if (item == null || item.isEmpty()) {
            return null;
        }
        if (item.has("continuationItemRenderer")) {
            return getNextPageFrom(item.getObject("continuationItemRenderer"));
        } else if (item.has("continuationItemViewModel")) {
            return getNextPageFrom(item.getObject("continuationItemViewModel"));
        } else if (item.has("backgroundPromoRenderer")) {
            final JsonObject backgroundPromo = item.getObject("backgroundPromoRenderer");
            final JsonObject bodyText = backgroundPromo == null
                    ? null : backgroundPromo.getObject("bodyText");
            throw new NothingFoundException(getTextFromObject(bodyText));
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
            return collectStreamItem(collector,
                    item.getObject("richItemRenderer").getObject("content"), timeAgoParser);
        } else if (item.has("gridShelfViewModel")) {
            return collectStreamsFromList(collector, item.getObject("gridShelfViewModel"),
                    timeAgoParser);
        } else if (item.has("reelShelfRenderer")) {
            return collectStreamsFromList(collector, item.getObject("reelShelfRenderer"),
                    timeAgoParser);
        } else if (item.has("shelfRenderer")) {
            return collectStreamItem(collector,
                    item.getObject("shelfRenderer").getObject("content"), timeAgoParser);
        } else if (item.has("verticalListRenderer")) {
            return collectStreamsFromList(collector,
                    item.getObject("verticalListRenderer"), timeAgoParser);
        } else if (item.has("horizontalListRenderer")) {
            return collectStreamsFromList(collector,
                    item.getObject("horizontalListRenderer"), timeAgoParser);
        } else if (item.has("expandedShelfContentsRenderer")) {
            return collectStreamsFromList(collector,
                    item.getObject("expandedShelfContentsRenderer"), timeAgoParser);
        } else if (item.has("itemSectionRenderer")) {
            return collectStreamsFrom(collector,
                    item.getObject("itemSectionRenderer").getArray("contents"),
                    timeAgoParser);
        } else if (item.has("richShelfRenderer")) {
            return collectStreamsFromList(collector, item.getObject("richShelfRenderer"),
                    timeAgoParser);
        } else if (item.has("richGridRenderer")) {
            return collectStreamsFromList(collector, item.getObject("richGridRenderer"),
                    timeAgoParser);
        } else if (item.has("richSectionRenderer")) {
            return collectStreamItem(collector,
                    item.getObject("richSectionRenderer").getObject("content"),
                    timeAgoParser);
        }
        return null;
    }

    @Nullable
    private Page collectStreamsFromList(final MultiInfoItemsCollector collector,
                                        final JsonObject list,
                                        final TimeAgoParser timeAgoParser)
            throws NothingFoundException, ParsingException {
        if (list == null || list.isEmpty()) {
            return null;
        }
        final JsonArray items = list.getArray("items");
        final JsonArray contents = items == null || items.isEmpty()
                ? list.getArray("contents") : items;
        return collectStreamsFrom(collector, contents, timeAgoParser);
    }

    @Nullable
    private Page getNextPageFrom(final JsonObject continuationItemRenderer) {
        if (isNullOrEmpty(continuationItemRenderer)) {
            return null;
        }

        final String token = getContinuationToken(continuationItemRenderer);
        if (isNullOrEmpty(token)) {
            return null;
        }

        final String url = YOUTUBEI_V1_URL + "search?"
                + DISABLE_PRETTY_PRINT_PARAMETER;

        return new Page(url, token);
    }

    @Nullable
    private String getContinuationToken(final JsonObject continuationItemRenderer) {
        if (continuationItemRenderer == null || continuationItemRenderer.isEmpty()) {
            return null;
        }
        final JsonObject continuationEndpoint = continuationItemRenderer
                .getObject("continuationEndpoint");
        final JsonObject endpoint = continuationEndpoint == null
                ? new JsonObject() : continuationEndpoint;
        final JsonObject continuationCommand = endpoint.getObject("continuationCommand");
        final String token = continuationCommand == null
                ? null : continuationCommand.getString("token");
        if (!isNullOrEmpty(token)) {
            return token;
        }

        final JsonObject viewModelCommand = continuationItemRenderer
                .getObject("continuationCommand");
        if (viewModelCommand != null) {
            final JsonObject innerTubeCommand = viewModelCommand.getObject("innertubeCommand");
            if (innerTubeCommand != null) {
                final JsonObject viewModelContinuation = innerTubeCommand
                        .getObject("continuationCommand");
                final String viewModelToken = viewModelContinuation == null
                        ? null : viewModelContinuation.getString("token");
                if (!isNullOrEmpty(viewModelToken)) {
                    return viewModelToken;
                }
            }
        }

        final JsonObject commandExecutor = endpoint.getObject("commandExecutorCommand");
        final JsonArray commands = commandExecutor == null
                ? null : commandExecutor.getArray("commands");
        if (commands == null) {
            return null;
        }
        for (final Object commandObject : commands) {
            if (!(commandObject instanceof JsonObject)) {
                continue;
            }
            final JsonObject command = (JsonObject) commandObject;
            if (command.has("continuationCommand")) {
                final JsonObject commandContinuation = command.getObject("continuationCommand");
                final String commandToken = commandContinuation == null
                        ? null : commandContinuation.getString("token");
                if (!isNullOrEmpty(commandToken)) {
                    return commandToken;
                }
            }
        }
        return null;
    }
}
