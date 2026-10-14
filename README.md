# TTRSS-Reader
TTRSS-Reader (or ttrss-reader-fork) is a fork of the original Project from [ttrss-reader](https://code.google.com/p/ttrss-reader/). I took the sourcecode and continued the development because the original owner didn't reply to any messages back then (and has not done so as of today).

It is a client application for the RSS-Reader [Tiny Tiny RSS](http://tt-rss.org/), a PHP-based online feedreader which runs on your own webspace. It is accessible with any browser and even supports keyboard shortcuts through javascript.
Download

Since google announced that the downloads from google-code will not be available after January 14th, 2014 I started to move all downloads to google-drive. The commentary with the current changelog will be removed in the process but the full changelog can be viewed in the Wiki.

[![play.google.com][3]][4]<br>
[![QR-Code for play.google.com][5]][4]

 * [**Download current Version**](https://github.com/nilsbraden/ttrss-reader-fork/releases)
 * ([**Changelog**](https://github.com/nilsbraden/ttrss-reader-fork/wiki/Changelog))

# Features
 * Use Swipe for Next/Previous Article
 * Swipe-Area in bottom area of the Article-View to avoid swiping while scrolling
 * Show or download images/videos/music attached to Articles
 * Share URL via Twitter/SMS/Mail
 * Mark everything (whole category or all categories) as read
 * Navigate through articles with volume-buttons
 * Read Labels
 * Cache articles in local-database
 * Cache images for offline browsing
 * SSL-Supoprt with self-signed certificates
 * Scheduled updates via Tasker/Locale
 * Fast JSON-Parsing with stream-based parser GSON 

See [Changelog](https://github.com/nilsbraden/ttrss-reader-fork/wiki/Changelog) for detailed information.

# Automatic synchronization
Under **Settings > System**, **Sync on startup and resume** is enabled by default.
Disable it to skip automatic category/feed overview synchronization when opening or
resuming the reader, and to prevent automatic startup image caching. Cached data
remains available; use **Refresh** to synchronize manually.

Manual image caching, article fetching while browsing, and Tasker/Locale updates
are unchanged. Disabling the setting does not cancel work already running or
change backend request timeouts.

# API timeouts
Under **Settings > Connection** two numeric settings control the HTTP timeout of
all backend API calls (connect, read and write):
 * **API timeout (seconds)**, default 10.
 * **Lazy server timeout (minutes)**, default 10. Used instead of the seconds
   value while **Lazy Server** is enabled.

Any positive whole number is accepted; both can also be set per Wi-Fi network.
Changes apply to the next API request without restarting the app. Image and
attachment downloads are not affected.

# API timeout diagnostics
Backend API timeout dialogs and logs include the called endpoint (`URL:`) and
the JSON request body (`JSON:`), including timeouts while reading a response.
Credentials and authentication/session tokens are replaced with
`VALUE_REDACTED`; the actual request is not modified. This includes login
usernames/passwords, `sid`, embedded URL credentials, and known credential/token
query parameters in the endpoint or body URL fields. Authorization headers are
never included.

All other parameters remain visible, including search text, article IDs, notes,
titles, article content, and ordinary URLs. Review diagnostics before sharing
them. Custom credential parameter names and secrets embedded in paths or
free-text fields are not automatically detected. Image/resource downloads and
retries are unchanged; see **API timeouts** for configuring timeout durations.

# Note regarding support and F-Droid
Since [F-Droid](https://f-droid.org/repository/browse/?fdid=org.ttrssreader) is often several months late with the build I have to make this clear: I won't provide support for versions which are not exactly or at least near to the version which is stated above on this site ("Current Version"). Please provide the full version information from the About-box within the app when reporting an issue!

# Changelog
Please follow this [link](https://github.com/nilsbraden/ttrss-reader-fork/wiki/Changelog)

# Contact
Please feel free to contact me via E-Mail (ttrss /at/ nilsbraden.de) or via the Bugtracker (Issues). If you have questions specifically about Tiny Tiny RSS you may ask me or visit the [Tiny-Tiny-RSS-Forum](http://tt-rss.org/forum/) or #tt-rss at irc.tt-rss.org. If I don't answer fast enough or am not willing to implement your suggestion please keep in mind that this is only a hobby for me and I'm unwilling to implement every little feature just beacuse you suggested I do so. You may implement it and send me pull-requests so I can include the features but I can't guarantee I will do this for every thing you want to have.

# Donations
If you would like to support my work and the further developement of TTRSS-reader you may submit a donation via Paypal here:
 * Paypal: [![paypal.com][1]][2]<br><br>

For more information you may want to visit [Donations](https://github.com/nilsbraden/ttrss-reader-fork/wiki/Donations).

# Screenshots
![Screenshot 1](http://nilsbraden.de/android/screenshots/small/device-2014-01-02-163718_1.png)
![Screenshot 2](http://nilsbraden.de/android/screenshots/small/device-2014-01-02-163709_1.png)
![Screenshot 3](http://nilsbraden.de/android/screenshots/small/device-2014-01-02-163755_1.png)
![Screenshot 4](http://nilsbraden.de/android/screenshots/small/device-2014-01-02-163740_1.png)
![Screenshot 5](http://nilsbraden.de/android/screenshots/small/Screenshot_2014-01-02-16-34-03_1.png)
![Screenshot 6](http://nilsbraden.de/android/screenshots/small/Screenshot_2014-01-02-16-34-16_1.png)
![Screenshot 7](http://nilsbraden.de/android/screenshots/small/Screenshot_2014-01-02-16-34-39_1.png)
![Screenshot 8](http://nilsbraden.de/android/screenshots/small/Screenshot_2014-01-02-16-35-11_1.png)
![Screenshot 9](http://nilsbraden.de/android/screenshots/small/Screenshot_2014-01-02-16-35-30_1.png)

[1]: https://nilsbraden.de/android/tt-rss/btn_donateCC_LG.gif
[2]: https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=SD4AN4APNW6B4
[3]: https://www.nilsbraden.de/android/tt-rss/google-play-badge.png
[4]: https://play.google.com/store/apps/details?id=org.ttrssreader
[5]: https://www.nilsbraden.de/android/tt-rss/qrcode.png
