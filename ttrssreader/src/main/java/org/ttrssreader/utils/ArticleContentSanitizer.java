package org.ttrssreader.utils;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

/**
 * Sanitizes article HTML before it is rendered in the article WebView.
 */
public final class ArticleContentSanitizer {

	private static final Pattern PROTOCOL_RELATIVE_MEDIA_SRC =
			Pattern.compile("(<(?:img|video|source)[^>]+?src=[\"'])//([^\"']*)", Pattern.CASE_INSENSITIVE);

	private static final String[] VIDEO_ATTRIBUTES = {"src", "poster", "controls", "loop", "muted", "playsinline",
			"preload", "width", "height"};

	private ArticleContentSanitizer() {
	}

	/**
	 * Removes everything that is not allowed by {@link Safelist#relaxed()} plus HTML5 video markup.
	 *
	 * @param html          the untrusted article content
	 * @param allowAutoplay whether the {@code autoplay} attribute of videos is kept
	 * @return the sanitized HTML
	 */
	public static String clean(String html, boolean allowAutoplay) {
		if (html == null)
			return "";
		return Jsoup.clean(html, safelist(allowAutoplay));
	}

	static Safelist safelist(boolean allowAutoplay) {
		Safelist safelist = Safelist.relaxed()
				.addTags("figure", "figcaption", "video", "source")
				.addAttributes("video", VIDEO_ATTRIBUTES)
				.addAttributes("source", "src", "type")
				.addProtocols("video", "src", "http", "https")
				.addProtocols("video", "poster", "http", "https")
				.addProtocols("source", "src", "http", "https");
		if (allowAutoplay)
			safelist.addAttributes("video", "autoplay");
		return safelist;
	}

	/**
	 * Some feeds use protocol-relative URLs ({@code //host/path}) which cannot be resolved against the local base
	 * URL of the WebView, so they are rewritten to https.
	 */
	public static String normalizeProtocolRelativeUrls(String html) {
		if (html == null)
			return null;
		return PROTOCOL_RELATIVE_MEDIA_SRC.matcher(html).replaceAll("$1https://$2");
	}
}
