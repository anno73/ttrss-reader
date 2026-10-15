/*
 * Copyright (c) 2015, Nils Braden
 *
 * This file is part of ttrss-reader-fork. This program is free software; you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation;
 * either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details. You should have received a
 * copy of the GNU General Public License along with this program; If
 * not, see http://www.gnu.org/licenses/.
 */

package org.ttrssreader.gui.view;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.ttrssreader.MyApplication;
import org.ttrssreader.R;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.controllers.Data;
import org.ttrssreader.gui.MediaPlayerActivity;
import org.ttrssreader.gui.fragments.ArticleFragment;
import org.ttrssreader.utils.AsyncTask;
import org.ttrssreader.utils.FileUtils;
import org.ttrssreader.utils.Utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;

import pub.devrel.easypermissions.EasyPermissions;

public class ArticleWebViewClient extends WebViewClient {

	private static final String TAG = ArticleWebViewClient.class.getSimpleName();

	public static final int RC_STORAGE = 13;

	private final ArticleFragment parentFragment;
	private final Context context;
	private String urlDownload;
	private boolean saveToPictures;

	public ArticleWebViewClient(ArticleFragment parentFragment) {
		this.parentFragment = parentFragment;
		this.context = parentFragment.getContext();
	}

	/*
	 * Uses old deprecated method call and should be removed some day but until then I won't duplicate the code
	 */
	@Override
	public boolean shouldOverrideUrlLoading(WebView view, final WebResourceRequest request) {
		return shouldOverrideUrlLoading(view, request.getUrl().toString());
	}

	@Override
	public boolean shouldOverrideUrlLoading(WebView view, final String url) {
		final Context context = view.getContext();

		boolean audio = false;
		boolean video = false;

		for (String s : FileUtils.AUDIO_EXTENSIONS) {
			if (url.toLowerCase(Locale.getDefault()).contains("." + s)) {
				audio = true;
				break;
			}
		}

		for (String s : FileUtils.VIDEO_EXTENSIONS) {
			if (url.toLowerCase(Locale.getDefault()).contains("." + s)) {
				video = true;
				break;
			}
		}

		final String contentType = audio ? "audio/*" : "video/*";

		if (audio || video) {
			// @formatter:off
			final CharSequence[] items = {
					context.getText(R.string.WebViewClientActivity_Display),
					context.getText(R.string.WebViewClientActivity_Download),
					context.getText(R.string.WebViewClientActivity_ChooseApp)};
			// @formatter:on

			AlertDialog.Builder builder = new AlertDialog.Builder(context);
			builder.setTitle("What shall we do?");
			builder.setItems(items, (dialog, item) -> {

				Intent intent = new Intent();
				switch (item) {
					case 0:
						Log.i(TAG, "Displaying file in mediaplayer: " + url);
						intent.setClass(context, MediaPlayerActivity.class);
						intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						intent.putExtra(MediaPlayerActivity.URL, url);
						context.startActivity(intent);
						break;
					case 1:
						checkStoragePermissionAndDownload(url);
						break;
					case 2:
						intent.setAction(Intent.ACTION_VIEW);
						intent.setDataAndType(Uri.parse(url), contentType);
						try {
							context.startActivity(intent);
						} catch (Exception e) {
							e.printStackTrace();
						}
						break;
					default:
						Log.e(TAG, "Doing nothing, but why is that?? Item: " + item);
						break;
				}
			});
			AlertDialog alert = builder.create();
			alert.show();
		} else {
			Uri uri = Uri.parse(url);

			try {
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(intent);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return true;
	}

	private void checkStoragePermissionAndDownload(String url) {
		urlDownload = url;
		saveToPictures = false;
		checkStoragePermission();
	}

	public void saveMediaToPictures(String url) {
		if (url == null || url.isEmpty()) {
			Log.w(TAG, "Cannot save media without a URL");
			return;
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			urlDownload = url;
			saveToPictures = true;
			checkStoragePermission();
		} else {
			startMediaSave(url);
		}
	}

	private void checkStoragePermission() {
		if (parentFragment == null)
			return;

		if (context == null)
			return;

		String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
		if (!EasyPermissions.hasPermissions(context, perms)) {
			// Do not have permissions, request now
			EasyPermissions.requestPermissions(parentFragment, context.getString(R.string.WebViewClientRequestStoragePermission), RC_STORAGE, perms);
		} else {
			downloadStoredUrl();
		}
	}

	public void downloadStoredUrl() {
		if (context == null || urlDownload == null)
			return;

		String url = urlDownload;
		boolean saveToPictures = this.saveToPictures;
		urlDownload = null;
		this.saveToPictures = false;

		if (saveToPictures) {
			startMediaSave(url);
			return;
		}

		Log.i(TAG, "Downloading file: " + url);
		try {
			new AsyncMediaDownloader(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new URL(url));
		} catch (MalformedURLException e) {
			Log.e(TAG, "Invalid media URL: " + url, e);
			Utils.showFinishedNotification("Invalid media URL: " + url, 0, true, context, Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
		}
	}

	public void onStoragePermissionResult(boolean granted) {
		if (urlDownload == null)
			return;

		if (granted) {
			downloadStoredUrl();
			return;
		}

		urlDownload = null;
		saveToPictures = false;
		if (context != null) {
			String message = context.getString(R.string.WebViewClientStoragePermissionDenied);
			Log.w(TAG, message);
			Utils.showBackgroundToast(context, message, android.widget.Toast.LENGTH_LONG);
			Utils.showFinishedNotification(message, 0, true, context, Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
		}
	}

	private void startMediaSave(String url) {
		if (context == null) {
			Log.e(TAG, "Cannot save media without a context");
			return;
		}
		try {
			new AsyncMediaSaver(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new URL(url));
		} catch (MalformedURLException e) {
			Log.e(TAG, "Invalid media URL: " + url, e);
			Utils.showFinishedNotification("Invalid media URL: " + url, 0, true, context, Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
		}
	}

	private static class AsyncMediaDownloader extends AsyncTask<URL, Void, Void> {
		private final String TAG = AsyncMediaDownloader.class.getSimpleName();
		private final static int BUFFER = (int) Utils.KB;
		private final WeakReference<Context> contextRef;

		AsyncMediaDownloader(Context context) {
			this.contextRef = new WeakReference<>(context);
		}

		protected Void doInBackground(URL... urls) {

			if (urls.length < 1) {
				String msg = "No URL given, skipping download...";
				Log.w(TAG, msg);
				Utils.showFinishedNotification(msg, 0, true, contextRef.get(), Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
				return null;
			} else if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
				String msg = "External Storage not available, skipping download...";
				Log.w(TAG, msg);
				Utils.showFinishedNotification(msg, 0, true, contextRef.get(), Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
				return null;
			}

			download(urls[0]);

			return null;
		}

		private void download(URL url) {
			long start = System.currentTimeMillis();
			Utils.showRunningNotification(contextRef.get(), false, Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);

			// Use configured output directory
			File folder = new File(Controller.getInstance().saveAttachmentPath());
			if (!folder.exists() && !folder.mkdirs()) {
				// Folder could not be created, fallback to internal directory on sdcard
				folder = MyApplication.context().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
				if (folder != null && !folder.exists() && !folder.mkdirs()) {
					String msg = "Folder could not be created: " + folder.getAbsolutePath();
					Log.w(TAG, msg);
					Utils.showFinishedNotification(msg, 0, true, contextRef.get(), Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
				}
			}

			BufferedInputStream bis;
			FileOutputStream fos;
			BufferedOutputStream bos = null;

			int size = -1;
			File file;
			try {
				URLConnection con = Controller.getInstance().openConnection(url);

				file = new File(folder, URLUtil.guessFileName(url.toString(), null, ".mp3"));
				if (file.exists()) {
					size = (int) file.length();
					con.setRequestProperty("Range", "bytes=" + size + "-"); // try to resume downloads
				}

				bis = new BufferedInputStream(con.getInputStream());
				fos = (size == 0) ? new FileOutputStream(file) : new FileOutputStream(file, true);
				bos = new BufferedOutputStream(fos, BUFFER);

				byte[] data = new byte[BUFFER];
				int count;
				while ((count = bis.read(data, 0, BUFFER)) >= 0) {
					bos.write(data, 0, count);
					size += count;
				}

				int time = (int) ((System.currentTimeMillis() - start) / Utils.SECOND);

				// Show Intent which opens the file
				Intent intent = new Intent();
				intent.setAction(android.content.Intent.ACTION_VIEW);

				// Only expose uri if allowed:
				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
					intent.setDataAndType(Uri.fromFile(file), FileUtils.getMimeType(file.getName()));
				}

				Log.i(TAG, "Finished. Path: " + file.getAbsolutePath() + " Time: " + time + "s Bytes: " + size);
				Utils.showFinishedNotification(file.getAbsolutePath(), time, false, contextRef.get(), intent, Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);

			} catch (IOException e) {
				String msg = "Error while downloading: " + e;
				Log.e(TAG, msg, e);
				Utils.showFinishedNotification(msg, 0, true, contextRef.get(), Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
			} finally {
				// Remove "running"-notification
				Utils.showRunningNotification(contextRef.get(), true, Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
				if (bos != null) {
					try {
						bos.close();
					} catch (IOException e) {
						// Empty!
					}
				}
			}
		}
	}

	private static class AsyncMediaSaver extends AsyncTask<URL, Void, Void> {
		private static final int BUFFER = (int) Utils.KB;
		private final WeakReference<Context> contextRef;

		AsyncMediaSaver(Context context) {
			contextRef = new WeakReference<>(context);
		}

		@Override
		protected Void doInBackground(URL... urls) {
			Context context = contextRef.get();
			if (context == null || urls.length == 0) {
				Log.e(TAG, "Cannot save media: context or URL is unavailable");
				return null;
			}
			if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
				String message = "External storage is unavailable; media was not saved.";
				Log.w(TAG, message);
				Utils.showFinishedNotification(message, 0, true, context, Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
				return null;
			}

			long start = System.currentTimeMillis();
			Utils.showRunningNotification(context, false, Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
			Uri savedUri = null;
			File savedFile = null;
			String fileName = null;

			try {
				URLConnection connection = Controller.getInstance().openConnection(urls[0]);
				fileName = URLUtil.guessFileName(urls[0].toString(), null, connection.getContentType());
				String mimeType = getMimeType(fileName);

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
					ContentValues values = new ContentValues();
					values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
					values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
					values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/tt-rss");
					values.put(MediaStore.MediaColumns.IS_PENDING, 1);

					savedUri = context.getContentResolver().insert(
							MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
					if (savedUri == null)
						throw new IOException("Could not create the media-store entry for " + fileName);

					try (InputStream input = new BufferedInputStream(connection.getInputStream());
						 OutputStream output = openBufferedOutput(context, savedUri, fileName)) {
						copy(input, output);
					}

					ContentValues completed = new ContentValues();
					completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
					if (context.getContentResolver().update(savedUri, completed, null, null) == 0)
						throw new IOException("Could not publish " + fileName);
				} else {
					File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "tt-rss");
					if (!directory.isDirectory() && !directory.mkdirs())
						throw new IOException("Could not create folder: " + directory.getAbsolutePath());

					savedFile = getAvailableFile(directory, fileName);
					try (InputStream input = new BufferedInputStream(connection.getInputStream());
						 OutputStream output = new BufferedOutputStream(new FileOutputStream(savedFile))) {
						copy(input, output);
					}
					MediaScannerConnection.scanFile(context, new String[]{savedFile.getAbsolutePath()},
							new String[]{mimeType}, null);
				}

				int seconds = (int) ((System.currentTimeMillis() - start) / Utils.SECOND);
				String savedName = savedFile == null ? fileName : savedFile.getName();
				Utils.showFinishedNotification("Saved to Pictures/tt-rss/" + savedName, seconds, false, context,
						Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
			} catch (IOException | SecurityException e) {
				if (savedUri != null && context.getContentResolver().delete(savedUri, null, null) == 0)
					Log.w(TAG, "Could not remove incomplete media-store entry for " + fileName);
				if (savedFile != null && !savedFile.delete())
					Log.w(TAG, "Could not remove incomplete saved file: " + savedFile.getAbsolutePath());
				String message = "Could not save media " + (fileName == null ? "" : fileName + ": ") + e.getMessage();
				Log.e(TAG, message, e);
				Utils.showFinishedNotification(message, 0, true, context, Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
			} finally {
				Utils.showRunningNotification(context, true, Data.NOTIFICATION_CHANNEL_ID_MEDIADOWNLOAD);
			}
			return null;
		}

		private static void copy(InputStream input, OutputStream output) throws IOException {
			byte[] buffer = new byte[BUFFER];
			int count;
			while ((count = input.read(buffer)) != -1)
				output.write(buffer, 0, count);
		}

		private static OutputStream openBufferedOutput(Context context, Uri uri, String fileName) throws IOException {
			OutputStream output = context.getContentResolver().openOutputStream(uri, "w");
			if (output == null)
				throw new IOException("Could not open the media-store entry for " + fileName);
			return new BufferedOutputStream(output);
		}

		private static String getMimeType(String fileName) {
			String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
			String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
			return mimeType == null ? "application/octet-stream" : mimeType;
		}

		private static File getAvailableFile(File directory, String fileName) throws IOException {
			File file = new File(directory, fileName);
			if (file.createNewFile())
				return file;

			int extensionStart = fileName.lastIndexOf('.');
			String baseName = extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName;
			String extension = extensionStart > 0 ? fileName.substring(extensionStart) : "";
			int suffix = 1;
			while (true) {
				file = new File(directory, baseName + " (" + suffix++ + ")" + extension);
				if (file.createNewFile())
					return file;
			}
		}
	}

	/*
	 * Uses old deprecated method call and should be removed some day but until then I won't duplicate the code
	 */
	@Override
	public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
		return shouldInterceptRequest(view, request.getUrl().toString());
	}

	/*
	 * WebKit does not call onReceivedHttpAuthRequest (or onReceivedError for that matter) when
	 * processing resources within a rendered document. As a result, it is not possible to
	 * inject authentication information without intercepting the resource loading itself.
	 */
	@Override
	public WebResourceResponse shouldInterceptRequest(WebView view, String urlStr) {
		if (!Controller.getInstance().useHttpAuth())
			return null;
		try {
			URL url = new URL(urlStr);
			if (!Controller.getInstance().urlNeedsAuthentication(url))
				return null;
			URLConnection c = Controller.getInstance().openConnection(url);
			return new WebResourceResponse(c.getContentType(), c.getContentEncoding(), c.getInputStream());
		} catch (IOException e) {
			Log.e(TAG, "Failed to fetch " + urlStr);
		}
		return null;
	}

}
