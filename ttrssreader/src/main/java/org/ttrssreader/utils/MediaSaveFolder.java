package org.ttrssreader.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import org.ttrssreader.controllers.Controller;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class MediaSaveFolder {
	private static final String TAG = MediaSaveFolder.class.getSimpleName();
	private static final int GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
	private static final Pattern CONTENT_DISPOSITION_FILENAME =
			Pattern.compile("(?:^|;)\\s*filename\\*?\\s*=", Pattern.CASE_INSENSITIVE);

	private MediaSaveFolder() {
	}

	public static Uri selectedUri() {
		String value = Controller.getInstance().mediaSaveFolder();
		return value.isEmpty() ? null : Uri.parse(value);
	}

	public static Intent pickerIntent() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		intent.addFlags(GRANTS | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
				| Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
		Uri selected = selectedUri();
		if (selected != null && DocumentsContract.isTreeUri(selected)
				&& Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentUri(selected));
		return intent;
	}

	public static void rememberSelection(Context context, Intent result) throws IOException {
		Uri tree = result == null ? null : result.getData();
		if (tree == null || !DocumentsContract.isTreeUri(tree)
				|| (result.getFlags() & GRANTS) != GRANTS)
			throw new IOException("The selected folder did not grant read and write access");

		ContentResolver resolver = context.getContentResolver();
		boolean alreadyGranted = hasPersistedAccess(resolver, tree);
		resolver.takePersistableUriPermission(tree, GRANTS);
		try {
			requireWritableFolder(context, tree);
		} catch (IOException | RuntimeException e) {
			if (!alreadyGranted) {
				try {
					resolver.releasePersistableUriPermission(tree, GRANTS);
				} catch (SecurityException cleanupError) {
					Log.w(TAG, "Could not release rejected media folder grant", cleanupError);
				}
			}
			throw e;
		}
		Controller.getInstance().setMediaSaveFolder(tree.toString());
	}

	public static void requireWritableFolder(Context context, Uri tree) throws IOException {
		if (!DocumentsContract.isTreeUri(tree))
			throw new IOException("The saved location is not a document folder");

		if (!hasPersistedAccess(context.getContentResolver(), tree))
			throw new IOException("Access to the saved folder is no longer granted");

		try (Cursor cursor = context.getContentResolver().query(documentUri(tree),
				new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS},
				null, null, null)) {
			if (cursor == null || !cursor.moveToFirst()
					|| !DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(0))
					|| (cursor.getInt(1) & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0)
				throw new IOException("The selected folder is unavailable or does not allow creating files");
		}
	}

	private static boolean hasPersistedAccess(ContentResolver resolver, Uri tree) {
		for (UriPermission permission : resolver.getPersistedUriPermissions()) {
			if (tree.equals(permission.getUri()) && permission.isReadPermission() && permission.isWritePermission()) {
				return true;
			}
		}
		return false;
	}

	public static String displayPath(Context context, Uri tree) throws IOException {
		Uri document = documentUri(tree);
		String selectedName = documentName(context, document);
		ContentResolver resolver = context.getContentResolver();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			try {
				DocumentsContract.Path path = DocumentsContract.findDocumentPath(resolver, document);
				if (path != null)
					return displayPath(context, tree, path, selectedName);
			} catch (UnsupportedOperationException ignored) {
				// Some document providers do not expose document ancestry.
			}
		}
		return externalStoragePath(context, tree, selectedName);
	}

	static String displayPath(Context context, Uri tree, DocumentsContract.Path path,
							  String selectedName) throws IOException {
		ContentResolver resolver = context.getContentResolver();
		RootInfo root = rootInfo(resolver, tree.getAuthority(), path.getRootId());
		List<String> parts = new ArrayList<>();
		List<String> documentIds = path.getPath();
		if (documentIds == null)
			return joinPath(root == null ? null : root.title, parts, selectedName);
		for (String documentId : documentIds) {
			if (documentId == null || documentId.isEmpty())
				continue;
			if (root == null || !documentId.equals(root.documentId))
				parts.add(documentName(context,
						DocumentsContract.buildDocumentUriUsingTree(tree, documentId)));
		}
		return joinPath(root == null ? null : root.title, parts, selectedName);
	}

	static String externalStoragePath(Context context, Uri tree, String selectedName) throws IOException {
		if (!"com.android.externalstorage.documents".equals(tree.getAuthority()))
			return selectedName;

		String documentId = DocumentsContract.getTreeDocumentId(tree);
		if (documentId == null)
			return selectedName;
		int separator = documentId.indexOf(':');
		if (separator <= 0)
			return selectedName;

		String volumeId = documentId.substring(0, separator);
		String relativePath = documentId.substring(separator + 1);
		List<String> parts = new ArrayList<>();
		for (String part : relativePath.split("/")) {
			if (!part.isEmpty())
				parts.add(part);
		}
		RootInfo root = rootInfo(context.getContentResolver(), tree.getAuthority(), volumeId);
		return joinPath(root == null ? null : root.title, parts, selectedName);
	}

	private static RootInfo rootInfo(ContentResolver resolver, String authority, String rootId) throws IOException {
		if (authority == null || rootId == null)
			return null;
		Uri roots = DocumentsContract.buildRootsUri(authority);
		try (Cursor cursor = resolver.query(roots, new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,
				DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_DOCUMENT_ID},
				null, null, null)) {
			if (cursor == null)
				return null;
			int rootIdColumn = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_ROOT_ID);
			int titleColumn = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_TITLE);
			int documentIdColumn = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_DOCUMENT_ID);
			if (rootIdColumn < 0 || titleColumn < 0 || documentIdColumn < 0)
				return null;
			while (cursor.moveToNext()) {
				if (rootId.equals(cursor.getString(rootIdColumn))) {
					String title = cursor.getString(titleColumn);
					String documentId = cursor.getString(documentIdColumn);
					return new RootInfo(title == null || title.isEmpty() ? null : title, documentId);
				}
			}
		}
		return null;
	}

	private static String joinPath(String rootTitle, List<String> parts, String fallback) {
		StringBuilder path = new StringBuilder();
		if (rootTitle != null && !rootTitle.isEmpty())
			path.append(rootTitle);
		for (String part : parts) {
			if (path.length() > 0)
				path.append('/');
			path.append(part);
		}
		return path.length() == 0 ? fallback : path.toString();
	}

	private static final class RootInfo {
		private final String title;
		private final String documentId;

		private RootInfo(String title, String documentId) {
			this.title = title;
			this.documentId = documentId;
		}
	}

	private static String documentName(Context context, Uri document) throws IOException {
		try (Cursor cursor = context.getContentResolver().query(document,
				new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
			if (cursor == null || !cursor.moveToFirst() || cursor.isNull(0))
				throw new IOException("Could not read the selected folder name");
			return cursor.getString(0);
		}
	}

	private static Uri documentUri(Uri tree) {
		return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
	}

	public static String fileName(String url, String disposition) {
		// A generic response MIME type must not replace the source file extension.
		String name = URLUtil.guessFileName(url, disposition, null);
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("[\\p{Cntrl}]", "_");
		return name.isEmpty() || ".".equals(name) || "..".equals(name) ? "download" : name;
	}

	public static boolean isCachedUrl(String url) {
		if (url == null)
			return false;
		int fragmentStart = url.indexOf('#');
		String withoutFragment = fragmentStart < 0 ? url : url.substring(0, fragmentStart);
		int queryStart = withoutFragment.indexOf('?');
		if (queryStart < 0 || !"public.php".equalsIgnoreCase(
				withoutFragment.substring(0, queryStart).replaceAll("^.*[/\\\\]", "")))
			return false;

		for (String parameter : withoutFragment.substring(queryStart + 1).split("&")) {
			int separator = parameter.indexOf('=');
			if (separator > 0 && "op".equalsIgnoreCase(parameter.substring(0, separator))
					&& "cached".equalsIgnoreCase(parameter.substring(separator + 1)))
				return true;
		}
		return false;
	}

	public static String fileName(String url, String disposition, String originalUrl, String responseType) {
		if (originalUrl != null && !originalUrl.isEmpty() && !isCachedUrl(originalUrl))
			return fileName(originalUrl, disposition);
		if (!isCachedUrl(url) && !isLocalFileUrl(url))
			return fileName(url, disposition);

		String guessed = fileName(url, disposition);
		if (CONTENT_DISPOSITION_FILENAME.matcher(disposition == null ? "" : disposition).find()
				&& !isCachedFileName(guessed) && !isLocalFileUrl(url))
			return guessed;
		return timestampFileName(responseType);
	}

	private static boolean isLocalFileUrl(String url) {
		return url != null && url.regionMatches(true, 0, "file:", 0, "file:".length());
	}

	private static boolean isCachedFileName(String name) {
		return "public.php".equalsIgnoreCase(name)
				|| name.toLowerCase(Locale.ROOT).startsWith("public.php.");
	}

	private static String timestampFileName(String responseType) {
		String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
		if (responseType == null)
			return timestamp;
		String type = responseType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
		if (extension == null || !extension.matches("[a-zA-Z0-9]+"))
			return timestamp;
		return timestamp + "." + extension.toLowerCase(Locale.ROOT);
	}

	public static String mimeType(String name, String responseType) {
		int dot = name.lastIndexOf('.');
		String inferred = dot < 0 ? null : MimeTypeMap.getSingleton()
				.getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
		if (inferred != null)
			return inferred;
		if (responseType != null) {
			String type = responseType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
			if (type.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+"))
				return type;
		}
		return "application/octet-stream";
	}

	static String availableName(String name, Set<String> names) {
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String extension = dot > 0 ? name.substring(dot) : "";
		String candidate = name;
		int suffix = 1;
		while (names.contains(candidate))
			candidate = base + " (" + suffix++ + ")" + extension;
		return candidate;
	}

	public static synchronized String write(Context context, Uri tree, String name, String mimeType,
											InputStream input) throws IOException {
		requireWritableFolder(context, tree);
		ContentResolver resolver = context.getContentResolver();
		Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
				DocumentsContract.getTreeDocumentId(tree));
		Set<String> names = new HashSet<>();
		try (Cursor cursor = resolver.query(children,
				new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
			if (cursor == null)
				throw new IOException("Could not list files in the selected folder");
			while (cursor.moveToNext())
				names.add(cursor.getString(0));
		}

		Uri created = DocumentsContract.createDocument(resolver, documentUri(tree), mimeType,
				availableName(name, names));
		if (created == null)
			throw new IOException("Could not create " + name + " in the selected folder");
		try {
			OutputStream stream = resolver.openOutputStream(created, "w");
			if (stream == null)
				throw new IOException("Could not open " + name + " for writing");
			try (OutputStream output = new BufferedOutputStream(stream)) {
				byte[] buffer = new byte[8192];
				int count;
				while ((count = input.read(buffer)) != -1)
					output.write(buffer, 0, count);
			}
			return documentName(context, created);
		} catch (IOException | RuntimeException e) {
			try {
				if (!DocumentsContract.deleteDocument(resolver, created))
					Log.w(TAG, "Could not remove incomplete media document: " + created);
			} catch (IOException | RuntimeException cleanupError) {
				Log.w(TAG, "Could not remove incomplete media document: " + created, cleanupError);
			}
			throw e;
		}
	}
}
