package org.ttrssreader.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.ttrssreader.controllers.Controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MediaSaveFolderTest {
	private final Context context = mock(Context.class);
	private final ContentResolver resolver = mock(ContentResolver.class);
	private final Controller controller = mock(Controller.class);
	private final Uri tree = mock(Uri.class);
	private final Uri folder = mock(Uri.class);
	private final Uri children = mock(Uri.class);
	private final Uri created = mock(Uri.class);
	private final Cursor metadata = mock(Cursor.class);
	private final Cursor listing = mock(Cursor.class);
	private final Cursor savedMetadata = mock(Cursor.class);
	private final UriPermission permission = mock(UriPermission.class);
	private MockedStatic<DocumentsContract> documents;
	private MockedStatic<Controller> controllers;
	private MockedStatic<Log> logs;

	@Before
	public void setUp() throws Exception {
		documents = mockStatic(DocumentsContract.class);
		controllers = mockStatic(Controller.class);
		logs = mockStatic(Log.class);
		controllers.when(Controller::getInstance).thenReturn(controller);
		when(context.getContentResolver()).thenReturn(resolver);
		when(tree.toString()).thenReturn("content://provider/tree/photos");
		documents.when(() -> DocumentsContract.isTreeUri(tree)).thenReturn(true);
		documents.when(() -> DocumentsContract.getTreeDocumentId(tree)).thenReturn("photos");
		documents.when(() -> DocumentsContract.buildDocumentUriUsingTree(tree, "photos")).thenReturn(folder);
		documents.when(() -> DocumentsContract.buildChildDocumentsUriUsingTree(tree, "photos")).thenReturn(children);
		when(permission.getUri()).thenReturn(tree);
		when(permission.isReadPermission()).thenReturn(true);
		when(permission.isWritePermission()).thenReturn(true);
		when(resolver.getPersistedUriPermissions()).thenReturn(Collections.singletonList(permission));
		when(resolver.query(eq(folder), any(String[].class), isNull(), isNull(), isNull())).thenReturn(metadata);
		when(metadata.moveToFirst()).thenReturn(true);
		when(metadata.getString(0)).thenReturn(DocumentsContract.Document.MIME_TYPE_DIR);
		when(metadata.getInt(1)).thenReturn(DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE);
		when(resolver.query(eq(children), any(String[].class), isNull(), isNull(), isNull())).thenReturn(listing);
		when(resolver.query(eq(created), any(String[].class), isNull(), isNull(), isNull())).thenReturn(savedMetadata);
		when(savedMetadata.moveToFirst()).thenReturn(true);
		when(savedMetadata.getString(0)).thenReturn("actual-name.jpg");
		documents.when(() -> DocumentsContract.createDocument(resolver, folder, "image/jpeg", "photo.jpg"))
				.thenReturn(created);
	}

	@After
	public void tearDown() {
		logs.close();
		controllers.close();
		documents.close();
	}

	@Test
	public void requiresPersistedReadAndWriteAccess() throws Exception {
		MediaSaveFolder.requireWritableFolder(context, tree);
		when(permission.isWritePermission()).thenReturn(false);
		assertThrows(IOException.class, () -> MediaSaveFolder.requireWritableFolder(context, tree));
	}

	@Test
	public void rejectsUnavailableOrReadOnlyFolder() {
		when(metadata.getInt(1)).thenReturn(0);
		assertThrows(IOException.class, () -> MediaSaveFolder.requireWritableFolder(context, tree));
		when(metadata.moveToFirst()).thenReturn(false);
		assertThrows(IOException.class, () -> MediaSaveFolder.requireWritableFolder(context, tree));
	}

	@Test
	public void remembersOnlyAValidatedSelection() throws Exception {
		Intent result = selection(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		MediaSaveFolder.rememberSelection(context, result);
		verify(resolver).takePersistableUriPermission(tree,
				Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		verify(controller).setMediaSaveFolder("content://provider/tree/photos");
	}

	@Test
	public void invalidSelectionDoesNotReplacePreviousFolder() {
		assertThrows(IOException.class, () -> MediaSaveFolder.rememberSelection(context, null));
		assertThrows(IOException.class,
				() -> MediaSaveFolder.rememberSelection(context, selection(Intent.FLAG_GRANT_READ_URI_PERMISSION)));
		verify(controller, never()).setMediaSaveFolder(anyString());
	}

	@Test
	public void failedPersistenceDoesNotReplacePreviousFolder() {
		doThrow(new SecurityException("Not persistable")).when(resolver)
				.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		assertThrows(SecurityException.class, () -> MediaSaveFolder.rememberSelection(context,
				selection(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)));
		verify(controller, never()).setMediaSaveFolder(anyString());
	}

	@Test
	public void failedValidationReleasesOnlyNewGrant() {
		when(resolver.getPersistedUriPermissions()).thenReturn(Collections.emptyList(),
				Collections.singletonList(permission));
		when(metadata.getInt(1)).thenReturn(0);
		assertThrows(IOException.class, () -> MediaSaveFolder.rememberSelection(context,
				selection(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)));
		verify(resolver).releasePersistableUriPermission(tree,
				Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		verify(controller, never()).setMediaSaveFolder(anyString());
	}

	@Test
	public void failedValidationDoesNotRevokePreviouslyPersistedAccess() {
		when(metadata.getInt(1)).thenReturn(0);
		assertThrows(IOException.class, () -> MediaSaveFolder.rememberSelection(context,
				selection(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)));
		verify(resolver, never()).releasePersistableUriPermission(any(), anyInt());
		verify(controller, never()).setMediaSaveFolder(anyString());
	}

	@Test
	public void collisionNamesKeepExtensionAndExistingFiles() {
		assertEquals("photo (2).jpg", MediaSaveFolder.availableName("photo.jpg",
				new HashSet<>(Arrays.asList("photo.jpg", "photo (1).jpg"))));
		assertEquals("clip (1).webm", MediaSaveFolder.availableName("clip.webm",
				Collections.singleton("clip.webm")));
		assertEquals("file (1)", MediaSaveFolder.availableName("file", Collections.singleton("file")));
		assertEquals(".hidden (1)", MediaSaveFolder.availableName(".hidden", Collections.singleton(".hidden")));
	}

	@Test
	public void originalNameUsesDispositionWithoutMimeBasedExtensionReplacement() {
		try (MockedStatic<URLUtil> urls = mockStatic(URLUtil.class)) {
			urls.when(() -> URLUtil.guessFileName("https://host/file", "attachment; filename=photo.jpg", null))
					.thenReturn("../photo.jpg");
			assertEquals("photo.jpg", MediaSaveFolder.fileName("https://host/file", "attachment; filename=photo.jpg"));
			urls.verify(() -> URLUtil.guessFileName("https://host/file", "attachment; filename=photo.jpg", null));
		}
	}

	@Test
	public void cachedImageUsesOriginalSourceFilenameWhenAvailable() {
		String cachedUrl = "https://tt-rss.example/public.php?op=cached&file=starred-images/253650-hash";
		String originalUrl = "https://64.media.tumblr.com/image/s1280x1920/original.jpg";
		try (MockedStatic<URLUtil> urls = mockStatic(URLUtil.class)) {
			urls.when(() -> URLUtil.guessFileName(originalUrl, null, null)).thenReturn("original.jpg");
			assertTrue(MediaSaveFolder.isCachedUrl(cachedUrl));
			assertFalse(MediaSaveFolder.isCachedUrl("https://64.media.tumblr.com/image/original.jpg"));
			assertEquals("original.jpg", MediaSaveFolder.fileName(cachedUrl, null, originalUrl, "image/jpeg"));
		}
	}

	@Test
	public void cachedImageUsesFilenameFromContentDisposition() {
		String cachedUrl = "https://tt-rss.example/public.php?op=cached&file=starred-images/253650-hash";
		try (MockedStatic<URLUtil> urls = mockStatic(URLUtil.class)) {
			urls.when(() -> URLUtil.guessFileName(cachedUrl, "attachment; filename=source.jpg", null))
					.thenReturn("source.jpg");
			assertEquals("source.jpg", MediaSaveFolder.fileName(cachedUrl,
					"attachment; filename=source.jpg", null, "image/jpeg"));
		}
	}

	@Test
	public void cachedImageUsesTimestampAndMimeExtensionWhenOriginalNameIsUnavailable() {
		String cachedUrl = "https://tt-rss.example/public.php?op=cached&file=starred-images/253650-hash";
		MimeTypeMap map = mock(MimeTypeMap.class);
		try (MockedStatic<URLUtil> urls = mockStatic(URLUtil.class);
			 MockedStatic<MimeTypeMap> maps = mockStatic(MimeTypeMap.class)) {
			urls.when(() -> URLUtil.guessFileName(cachedUrl, null, null)).thenReturn("public.php.jpg");
			maps.when(MimeTypeMap::getSingleton).thenReturn(map);
			when(map.getExtensionFromMimeType("image/jpeg")).thenReturn("jpg");

			String name = MediaSaveFolder.fileName(cachedUrl, null, null, "image/jpeg");
			assertTrue(name, name.matches("\\d{8}-\\d{6}\\.jpg"));
			assertFalse(name, name.contains("public.php"));
		}
	}

	@Test
	public void locallyCachedImageDoesNotUseCacheKeyAsFilename() {
		String cachedUrl = "file:///data/data/app/cache/https+++tt-rss.example+public.php+op+cached+file+starred-images";
		MimeTypeMap map = mock(MimeTypeMap.class);
		try (MockedStatic<URLUtil> urls = mockStatic(URLUtil.class);
			 MockedStatic<MimeTypeMap> maps = mockStatic(MimeTypeMap.class)) {
			urls.when(() -> URLUtil.guessFileName(cachedUrl, null, null)).thenReturn("https+++tt-rss.example.jpg");
			maps.when(MimeTypeMap::getSingleton).thenReturn(map);
			when(map.getExtensionFromMimeType("image/jpeg")).thenReturn("jpg");

			String name = MediaSaveFolder.fileName(cachedUrl, null, null, "image/jpeg");
			assertTrue(name, name.matches("\\d{8}-\\d{6}\\.jpg"));
			assertFalse(name, name.contains("tt-rss"));
		}
	}

	@Test
	public void mimeTypeSupportsImagesVideoAndGenericResponseHeaders() {
		MimeTypeMap map = mock(MimeTypeMap.class);
		try (MockedStatic<MimeTypeMap> maps = mockStatic(MimeTypeMap.class)) {
			maps.when(MimeTypeMap::getSingleton).thenReturn(map);
			when(map.getMimeTypeFromExtension("jpg")).thenReturn("image/jpeg");
			when(map.getMimeTypeFromExtension("png")).thenReturn("image/png");
			when(map.getMimeTypeFromExtension("gif")).thenReturn("image/gif");
			when(map.getMimeTypeFromExtension("webm")).thenReturn("video/webm");
			assertEquals("image/jpeg", MediaSaveFolder.mimeType("photo.JPG", "application/octet-stream"));
			assertEquals("image/png", MediaSaveFolder.mimeType("photo.png", null));
			assertEquals("image/gif", MediaSaveFolder.mimeType("animation.gif", null));
			assertEquals("video/webm", MediaSaveFolder.mimeType("clip.webm", null));
			assertEquals("image/jpeg", MediaSaveFolder.mimeType("image", "image/jpeg; charset=binary"));
			assertEquals("application/octet-stream", MediaSaveFolder.mimeType("image", "invalid"));
		}
	}

	@Test
	public void writesExactBytesAndReturnsProviderAssignedName() throws Exception {
		byte[] bytes = {0, 1, 2, -1, 10, 13};
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		when(resolver.openOutputStream(created, "w")).thenReturn(output);
		assertEquals("actual-name.jpg", MediaSaveFolder.write(context, tree, "photo.jpg", "image/jpeg",
				new ByteArrayInputStream(bytes)));
		assertArrayEquals(bytes, output.toByteArray());
		documents.verify(() -> DocumentsContract.deleteDocument(resolver, created), never());
	}

	@Test
	public void writeUsesNonCollidingDocumentName() throws Exception {
		when(listing.moveToNext()).thenReturn(true, false);
		when(listing.getString(0)).thenReturn("photo.jpg");
		documents.when(() -> DocumentsContract.createDocument(resolver, folder, "image/jpeg", "photo (1).jpg"))
				.thenReturn(created);
		when(resolver.openOutputStream(created, "w")).thenReturn(new ByteArrayOutputStream());
		MediaSaveFolder.write(context, tree, "photo.jpg", "image/jpeg", new ByteArrayInputStream(new byte[]{1}));
		documents.verify(() -> DocumentsContract.createDocument(resolver, folder, "image/jpeg", "photo (1).jpg"));
	}

	@Test
	public void writeFailureDeletesOnlyNewDocumentAndPreservesOriginalError() throws Exception {
		OutputStream output = mock(OutputStream.class);
		IOException failure = new IOException("Disk full");
		doThrow(failure).when(output).write(any(byte[].class), anyInt(), anyInt());
		when(resolver.openOutputStream(created, "w")).thenReturn(output);
		documents.when(() -> DocumentsContract.deleteDocument(resolver, created))
				.thenThrow(new SecurityException("Cleanup denied"));
		assertSame(failure, assertThrows(IOException.class, () -> MediaSaveFolder.write(context, tree,
				"photo.jpg", "image/jpeg", new ByteArrayInputStream(new byte[]{1}))));
		documents.verify(() -> DocumentsContract.deleteDocument(resolver, created));
		verify(output).close();
	}

	@Test
	public void missingOutputStreamFailsAndRemovesIncompleteDocument() {
		assertThrows(IOException.class, () -> MediaSaveFolder.write(context, tree, "photo.jpg", "image/jpeg",
				new ByteArrayInputStream(new byte[]{1})));
		documents.verify(() -> DocumentsContract.deleteDocument(resolver, created));
	}

	@Test
	public void missingChildListingFailsBeforeCreatingAnything() {
		when(resolver.query(eq(children), any(String[].class), isNull(), isNull(), isNull())).thenReturn(null);
		assertThrows(IOException.class, () -> MediaSaveFolder.write(context, tree, "photo.jpg", "image/jpeg",
				new ByteArrayInputStream(new byte[]{1})));
		documents.verify(() -> DocumentsContract.createDocument(any(), any(), anyString(), anyString()), never());
	}

	@Test
	public void saverDoesNotRequirePrimaryExternalStorage() throws Exception {
		URL url = new URL("https://host/photo.jpg");
		URLConnection connection = mock(URLConnection.class);
		when(context.getApplicationContext()).thenReturn(context);
		when(controller.openConnection(url)).thenReturn(connection);
		when(connection.getURL()).thenReturn(url);
		when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1}));
		when(connection.getContentType()).thenReturn("image/jpeg");
		try (MockedStatic<MediaSaveFolder> folders = mockStatic(MediaSaveFolder.class);
			 MockedStatic<Environment> storage = mockStatic(Environment.class);
			 MockedStatic<Utils> utils = mockStatic(Utils.class)) {
			storage.when(Environment::getExternalStorageState).thenReturn(Environment.MEDIA_UNMOUNTED);
			folders.when(() -> MediaSaveFolder.displayName(context, tree)).thenReturn("Selected folder");
			folders.when(() -> MediaSaveFolder.fileName(url.toString(), null, url.toString(), "image/jpeg"))
					.thenReturn("photo.jpg");
			folders.when(() -> MediaSaveFolder.mimeType("photo.jpg", "image/jpeg")).thenReturn("image/jpeg");
			folders.when(() -> MediaSaveFolder.write(eq(context), eq(tree), eq("photo.jpg"),
					eq("image/jpeg"), any())).thenReturn("photo.jpg");

			Class<?> saver = Class.forName("org.ttrssreader.gui.view.ArticleWebViewClient$AsyncMediaSaver");
			Constructor<?> constructor = saver.getDeclaredConstructor(Context.class, Uri.class, String.class);
			constructor.setAccessible(true);
			Object instance = constructor.newInstance(context, tree, url.toString());
			Method background = saver.getDeclaredMethod("doInBackground", URL[].class);
			background.setAccessible(true);
			background.invoke(instance, (Object) new URL[]{url});

			folders.verify(() -> MediaSaveFolder.fileName(url.toString(), null, url.toString(), "image/jpeg"));
			folders.verify(() -> MediaSaveFolder.write(eq(context), eq(tree), eq("photo.jpg"),
					eq("image/jpeg"), any()));
			storage.verify(Environment::getExternalStorageState, never());
		}
	}

	private Intent selection(int flags) {
		Intent result = mock(Intent.class);
		when(result.getData()).thenReturn(tree);
		when(result.getFlags()).thenReturn(flags);
		return result;
	}
}
