package org.ttrssreader.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;

import static org.junit.Assert.*;

public class ArticleContentSanitizerTest {

	private static final String POSTER = "https://44.media.tumblr.com/poster/s688x464_f1/frame.jpg";
	private static final String VIDEO = "https://64.media.tumblr.com/clip/1ef01217e85e1c20cb3f6a3218490a515f012122.mov";
	// Content shape of tt-rss article 314961 (feed 319)
	private static final String TUMBLR_VIDEO = "<figure><video controls=\"controls\" autoplay=\"autoplay\" "
			+ "playsinline=\"playsinline\" muted=\"muted\" poster=\"" + POSTER + "\"><source src=\"" + VIDEO
			+ "\" type=\"video/mp4\"></source></video></figure>";

	private static Document clean(String html, boolean autoplay) {
		return Jsoup.parseBodyFragment(ArticleContentSanitizer.clean(html, autoplay));
	}

	@Test
	public void keepsVideoWithSourcePosterAndControls() {
		Document doc = clean(TUMBLR_VIDEO, true);
		Element video = doc.selectFirst("figure > video");
		assertNotNull(video);
		assertEquals(POSTER, video.attr("poster"));
		assertTrue(video.hasAttr("controls"));
		assertTrue(video.hasAttr("muted"));
		assertTrue(video.hasAttr("playsinline"));
		Element source = video.selectFirst("source");
		assertNotNull(source);
		assertEquals(VIDEO, source.attr("src"));
		assertEquals("video/mp4", source.attr("type"));
	}

	@Test
	public void keepsAutoplayWhenAllowed() {
		assertTrue(clean(TUMBLR_VIDEO, true).selectFirst("video").hasAttr("autoplay"));
	}

	@Test
	public void stripsAutoplayButKeepsControlsWhenDisallowed() {
		Element video = clean(TUMBLR_VIDEO, false).selectFirst("video");
		assertNotNull(video);
		assertFalse(video.hasAttr("autoplay"));
		assertTrue(video.hasAttr("controls"));
		assertEquals(VIDEO, video.selectFirst("source").attr("src"));
	}

	@Test
	public void keepsVideoSrcAttribute() {
		Element video = clean("<video controls src=\"https://example.org/a.mp4\"></video>", true).selectFirst("video");
		assertNotNull(video);
		assertEquals("https://example.org/a.mp4", video.attr("src"));
	}

	@Test
	public void keepsImagesAsBefore() {
		Element img = clean("<p><img src=\"https://example.org/a.jpg\" alt=\"Alt\" title=\"Title\"></p>", true)
				.selectFirst("img");
		assertNotNull(img);
		assertEquals("https://example.org/a.jpg", img.attr("src"));
		assertEquals("Alt", img.attr("alt"));
		assertEquals("Title", img.attr("title"));
	}

	@Test
	public void keepsFigureCaption() {
		Element caption = clean(TUMBLR_VIDEO.replace("</figure>", "<figcaption>Caption</figcaption></figure>"), true)
				.selectFirst("figure > figcaption");
		assertNotNull(caption);
		assertEquals("Caption", caption.text());
	}

	@Test
	public void removesScriptsEventHandlersAndUnsafeUrls() {
		String html = "<script>alert(1)</script>"
				+ "<video onplay=\"alert(2)\" poster=\"javascript:alert(3)\" src=\"data:video/mp4;base64,AAAA\" controls>"
				+ "<source src=\"javascript:alert(4)\" onerror=\"alert(5)\" type=\"video/mp4\"></video>"
				+ "<iframe src=\"https://example.org\"></iframe>";
		String cleaned = ArticleContentSanitizer.clean(html, true);
		assertFalse(cleaned.contains("script"));
		assertFalse(cleaned.contains("alert"));
		assertFalse(cleaned.contains("data:"));
		assertFalse(cleaned.contains("iframe"));
		Document doc = Jsoup.parseBodyFragment(cleaned);
		Element video = doc.selectFirst("video");
		assertNotNull(video);
		assertFalse(video.hasAttr("onplay"));
		assertFalse(video.hasAttr("poster"));
		assertFalse(video.hasAttr("src"));
		assertFalse(doc.selectFirst("source").hasAttr("src"));
	}

	@Test
	public void handlesNullContent() {
		assertEquals("", ArticleContentSanitizer.clean(null, true));
		assertNull(ArticleContentSanitizer.normalizeProtocolRelativeUrls(null));
	}

	@Test
	public void rewritesProtocolRelativeMediaUrls() {
		String html = "<img src=\"//a.example/x.jpg\"><video poster=\"x\" src='//b.example/y.mp4'>"
				+ "<source type=\"video/mp4\" src=\"//c.example/z.mov\"></video><a href=\"//d.example/\">l</a>";
		String normalized = ArticleContentSanitizer.normalizeProtocolRelativeUrls(html);
		assertTrue(normalized.contains("src=\"https://a.example/x.jpg\""));
		assertTrue(normalized.contains("src='https://b.example/y.mp4'"));
		assertTrue(normalized.contains("src=\"https://c.example/z.mov\""));
		assertTrue(normalized.contains("href=\"//d.example/\""));
	}

	@Test
	public void protocolRelativeSourceSurvivesSanitizing() {
		String html = ArticleContentSanitizer.normalizeProtocolRelativeUrls(
				"<video controls><source src=\"//c.example/z.mov\" type=\"video/mp4\"></video>");
		assertEquals("https://c.example/z.mov", clean(html, true).selectFirst("source").attr("src"));
	}
}
