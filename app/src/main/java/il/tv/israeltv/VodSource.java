package il.tv.israeltv;

/**
 * VOD library definition for one broadcaster.
 *
 * "By time" (first priority) has three modes:
 *   TIME_KAN_SCHEDULE - native day-by-day broadcast schedule (Kan's schedule endpoint),
 *                       each past item links to the show -> episodes.
 *   TIME_LATEST       - newest episodes scraped from the site's VOD page
 *                       (sorted by the numeric id at the end of the link when sortById).
 *   TIME_PAGE         - just open the site's VOD home page.
 *
 * "By programs" (second priority): programs index page -> program page -> episodes.
 * A null regex means "generic": any same-site link that has a picture.
 */
public class VodSource {
    public static final int TIME_KAN_SCHEDULE = 1;
    public static final int TIME_LATEST = 2;
    public static final int TIME_PAGE = 3;

    public final String name;
    public final int timeMode;
    public final String timeArg;       // kan channelId, or URL for LATEST / PAGE
    public final String latestRegex;   // for TIME_LATEST
    public final boolean sortById;     // for TIME_LATEST
    public final String programsUrl;
    public final String programRegex;
    public final String episodeRegex;

    public VodSource(String name, int timeMode, String timeArg, String latestRegex, boolean sortById,
                     String programsUrl, String programRegex, String episodeRegex) {
        this.name = name;
        this.timeMode = timeMode;
        this.timeArg = timeArg;
        this.latestRegex = latestRegex;
        this.sortById = sortById;
        this.programsUrl = programsUrl;
        this.programRegex = programRegex;
        this.episodeRegex = episodeRegex;
    }

    private static final String KAN_PROGRAM = "kan\\.org\\.il/content/[^/]+/[^/]+/p-\\d+/?$";
    private static final String KAN_EPISODE = "kan\\.org\\.il/content/[^/]+/[^/]+/p-\\d+/(s\\d+/)?\\d+/?$";
    private static final String R13_EPISODE = "13tv\\.co\\.il/allshows/series/\\d+/season/\\d+/\\d+/?$";
    private static final String MAKO_PROGRAM =
            "mako\\.co\\.il/mako-vod-(?!live-tv|index|login|purchase|help)[a-z0-9_-]+/[^/?#.]+/?$";
    private static final String MAKO_EPISODE = "mako\\.co\\.il/mako-vod-[^?#]*/VOD-[0-9a-f]+\\.htm";

    public static final VodSource[] ALL = {
            new VodSource("כאן 11",
                    TIME_KAN_SCHEDULE, "4444", null, false,
                    "https://www.kan.org.il/lobby/kan11/", KAN_PROGRAM, KAN_EPISODE),
            new VodSource("קשת 12",
                    TIME_LATEST, "https://www.mako.co.il/mako-vod", MAKO_EPISODE, false,
                    "https://www.mako.co.il/mako-vod-index", MAKO_PROGRAM, MAKO_EPISODE),
            new VodSource("רשת 13",
                    TIME_LATEST, "https://13tv.co.il/allshows/", R13_EPISODE, true,
                    "https://13tv.co.il/allshows/", "13tv\\.co\\.il/allshows/series/\\d+/?$", R13_EPISODE),
            new VodSource("עכשיו 14",
                    TIME_LATEST, "https://tv.c14.co.il/", null, false,
                    "https://tv.c14.co.il/programs", null, null),
            new VodSource("כאן חינוכית",
                    TIME_KAN_SCHEDULE, "4471", null, false,
                    "https://www.kankids.org.il/", null, null),
    };
}
