/* 
Copyright Paul James Mutton, 2001-2004, http://www.jibble.org/

This file is part of Mini Wegb Server / SimpleWebServer.

This software is dual-licensed, allowing you to choose between the GNU
General Public License (GPL) and the www.jibble.org Commercial License.
Since the GPL may be too restrictive for use in a proprietary application,
a commercial license is also provided. Full license information can be
found at http://www.jibble.org/licenses/

$Author: pjm2 $
$Id: ServerSideScriptEngine.java,v 1.4 2004/02/01 13:37:35 pjm2 Exp $

 */

package org.jibble.simplewebserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Date;
import java.util.List;

import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Copyright Paul Mutton http://www.jibble.org/ This version is available at
 * https://github.com/rafaelsteil/simple-webserver
 */
public class RequestThread extends Thread {

	static ConfigurationBuilder CB = new ConfigurationBuilder();
	static {
		CB.setDebugEnabled(true)
				.setOAuthConsumerKey("GQyCKJBmiufakgJ7P5T1eAsxV")
				.setOAuthConsumerSecret(
						"Hmwv71tVYpHOSOrNT7w0WGdb71JG5Wgxcfo3Gn2qDlhmbtWs2w")
				.setOAuthAccessToken(
						"54256387-TFUcMqAJdEMDWjyMOmsXMhyi4B95cxakSfF3aQ6tv")
				.setOAuthAccessTokenSecret(
						"d45FxNmL5NiVsO5EWzZhPECmqEycAwSMO5Jk8jebGBqbR");
	}
	static TwitterFactory TF = new TwitterFactory(CB.build());

	public RequestThread(Socket socket, File rootDir) {
		_socket = socket;
		_rootDir = rootDir;
	}

	private static void sendHeader(BufferedOutputStream out, int code,
			String contentType, long contentLength, long lastModified)
			throws IOException {
		out.write(("HTTP/1.0 "
				+ code
				+ " OK\r\n"
				+ "Date: "
				+ new Date().toString()
				+ "\r\n"
				+ "Server: JibbleWebServer/1.0\r\n"
				+ "Content-Type: "
				+ contentType
				+ "\r\n"
				+ "Expires: Thu, 01 Dec 1994 16:00:00 GMT\r\n"
				+ ((contentLength != -1) ? "Content-Length: " + contentLength
						+ "\r\n" : "") + "Last-modified: "
				+ new Date(lastModified).toString() + "\r\n" + "\r\n")
				.getBytes());
	}

	private static void sendError(BufferedOutputStream out, int code,
			String message) throws IOException {
		message = message + "<hr>" + SimpleWebServer.VERSION;
		sendHeader(out, code, "text/html", message.length(),
				System.currentTimeMillis());
		out.write(message.getBytes());
		out.flush();
		out.close();
	}

	public void run() {
		InputStream reader = null;
		try {
			_socket.setSoTimeout(30000);
			BufferedReader in = new BufferedReader(new InputStreamReader(
					_socket.getInputStream()));
			BufferedOutputStream out = new BufferedOutputStream(
					_socket.getOutputStream());

			String request = in.readLine();
			if (request == null
					|| !request.startsWith("GET ")
					|| !(request.endsWith(" HTTP/1.0") || request
							.endsWith("HTTP/1.1"))) {
				// Invalid request type (no "GET")
				sendError(out, 500, "Invalid Method.");
				return;
			}
			String path = request.substring(4, request.length() - 9);

			if (path.indexOf("collection") > -1) {

				Twitter twitter = TF.getInstance();
				try {

					// Uncomment to send tweet and test credentials
					// Status status = twitter.updateStatus("Try this tweet.");

					String id = "custom-549647846786347008";
					int collectionIdIdx = path.indexOf("custom-");
					if (collectionIdIdx > 0){
						id = path.substring(collectionIdIdx);
					}
					List<Status> statuses = twitter.getCustomTimeline(id);

					sendHeader(out, 200, "text/html", -1,
							System.currentTimeMillis());

					String title = "Tweets for '" + id + "'";
					out.write(("<html><head><title>" + title
							+ "</title></head><body><h3>" + title + "</h3><p>\n")
							.getBytes());

					for (int i = 0; i < statuses.size(); i++) {
						Status status = statuses.get(i);
						User user = status.getUser();
						String blockquote = "<blockquote class=\"twitter-tweet\" lang=\"en\"><p>"
								+ status.getText()
								+ "</p>&mdash; "
								+ user.getName()
								+ " ("
								+ user.getScreenName()
								+ ") <a href=\"https://twitter.com/"
								+ user.getScreenName()
								+ "/status/"
								+ status.getId() + "\">DATE5</a></blockquote>";
						out.write((blockquote + "<br>\n").getBytes());

					}
					out.write(("</p><hr><p>" + SimpleWebServer.VERSION + "</p><script async src=\"//platform.twitter.com/widgets.js\" charset=\"utf-8\"></script></body><html>")
							.getBytes());

				} catch (TwitterException e) {
					sendError(out, 500, "Invalid Method.");
				}

				out.flush();
				out.close();

				return;

			}

			File file = new File(_rootDir, URLDecoder.decode(path, "UTF-8"))
					.getCanonicalFile();

			if (file.isDirectory()) {
				// Check to see if there is an index file in the directory.
				File indexFile = new File(file, "index.html");
				if (indexFile.exists() && !indexFile.isDirectory()) {
					file = indexFile;
				}
			}

			if (!file.toString().startsWith(_rootDir.toString())) {
				// Uh-oh, it looks like some lamer is trying to take a peek
				// outside of our web root directory.
				sendError(out, 403, "Permission Denied.");
			} else if (!file.exists()) {
				// The file was not found.
				sendError(out, 404, "File Not Found.");
			} else if (file.isDirectory()) {
				// print directory listing
				if (!path.endsWith("/")) {
					path = path + "/";
				}
				File[] files = file.listFiles();
				sendHeader(out, 200, "text/html", -1,
						System.currentTimeMillis());
				String title = "Index of " + path;
				out.write(("<html><head><title>" + title
						+ "</title></head><body><h3>Index of " + path + "</h3><p>\n")
						.getBytes());
				for (int i = 0; i < files.length; i++) {
					file = files[i];
					String filename = file.getName();
					String description = "";
					if (file.isDirectory()) {
						description = "&lt;DIR&gt;";
					}
					out.write(("<a href=\"" + path + filename + "\">"
							+ filename + "</a> " + description + "<br>\n")
							.getBytes());
				}
				out.write(("</p><hr><p>" + SimpleWebServer.VERSION + "</p></body><html>")
						.getBytes());
			} else {
				reader = new BufferedInputStream(new FileInputStream(file));

				String contentType = (String) SimpleWebServer.MIME_TYPES
						.get(Utils.getExtension(file));
				if (contentType == null) {
					contentType = "application/octet-stream";
				}

				sendHeader(out, 200, contentType, file.length(),
						file.lastModified());

				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = reader.read(buffer)) != -1) {
					out.write(buffer, 0, bytesRead);
				}
				reader.close();
			}
			out.flush();
			out.close();
		} catch (IOException e) {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception anye) {
					// Do nothing.
				}
			}
		}
	}

	private File _rootDir;
	private Socket _socket;

}