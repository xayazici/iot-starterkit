package commons.connectivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.xml.bind.DatatypeConverter;

import com.google.gson.JsonSyntaxException;

import commons.utils.FileUtil;

public class HttpClient
extends AbstractClient {

	private HttpURLConnection connection;

	private String user;

	private String password;

	private SSLSocketFactory sslSocketFactory;

	private String destination;

	private HttpClient() {
		super();
	}

	public HttpClient(String user, String password) {
		this();

		this.user = user;
		this.password = password;
	}

	public HttpClient(SSLSocketFactory sslSocketFactory) {
		this();

		this.sslSocketFactory = sslSocketFactory;
	}

	public void connect(String destination)
	throws IOException {

		this.destination = destination;
		connection = openConnection(destination);

		if (user != null && password != null) {
			String base64 = DatatypeConverter
				.printBase64Binary((user + ":" + password).getBytes(ENCODING));
			connection.setRequestProperty("Authorization", "Basic " + base64);
		}
		else if (sslSocketFactory != null && connection instanceof HttpsURLConnection) {
			((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
		}
		else {
			throw new IOException("No authorization details provided");
		}
	}

	public void disconnect() {
		if (connection != null) {
			connection.disconnect();
			connection = null;
		}
	}

	public <T> void send(T payload, Class<T> clazz)
	throws IOException {
		doPostJson(payload, clazz);
	}

	public <T> T doGetJson(Class<T> clazz)
	throws IOException {
		try {
			return jsonParser.fromJson(doGetString(), clazz);
		}
		catch (JsonSyntaxException e) {
			throw new IOException("Unexpected JSON format returned", e);
		}
	}

	public String doGetString()
	throws IOException {

		if (connection == null) {
			connect(destination);
		}

		connection.setRequestMethod("GET");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setUseCaches(false);
		connection.setRequestProperty("Content-Type", "application/json");

		try {
			Response response = connect(connection);
			String body = response.getBody();

			System.out.println(String.format("Response [%1$d] %2$s", response.getCode(), body));

			return body;
		}
		finally {
			disconnect();
		}
	}

	public <T> T doPostJson(T payload, Class<T> clazz)
	throws IOException {
		String response = doPost(jsonParser.toJson(payload));
		try {
			return jsonParser.fromJson(response, clazz);
		}
		catch (JsonSyntaxException e) {
			throw new IOException("Unexpected JSON format returned", e);
		}
	}

	public <T> void doPost(T payload, Class<T> clazz)
	throws IOException {
		doPost(jsonParser.toJson(payload));
	}

	public String doPost(String request)
	throws IOException {

		if (connection == null) {
			connect(destination);
		}

		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setUseCaches(false);
		connection.setRequestProperty("Content-Type", "application/json");

		System.out.println(String.format("Request %1$s", request));
		System.out.println();

		byte[] bytes = request.getBytes(ENCODING);

		OutputStream os = connection.getOutputStream();
		try {
			os.write(bytes);
		}
		finally {
			FileUtil.closeStream(os);
		}

		try {
			Response response = connect(connection);
			String body = response.getBody();

			System.out.println(String.format("Response [%1$d] %2$s", response.getCode(), body));

			return body;
		}
		finally {
			disconnect();
		}
	}

	private HttpURLConnection openConnection(String destination)
	throws IOException {

		disconnect();

		System.out.println(String.format("Connect to %1$s", destination));
		System.out.println();

		URI uri = null;
		try {
			URL url = new URL(destination);
			uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(),
				url.getPath(), url.getQuery(), null);
		}
		catch (MalformedURLException | URISyntaxException e) {
			throw new IOException("Invalid HTTPS connection URL specified", e);
		}

		HttpURLConnection connection = null;
		try {
			connection = (HttpsURLConnection) uri.toURL().openConnection();
		}
		catch (IOException e) {
			throw new IOException("Unable to open a HTTP connection", e);
		}

		return connection;
	}

	private Response connect(HttpURLConnection connection)
	throws IOException {

		connection.connect();

		int code = connection.getResponseCode();

		InputStream stream;
		if (code < HttpURLConnection.HTTP_OK || code >= HttpURLConnection.HTTP_MOVED_PERM) {
			stream = connection.getErrorStream();
		}
		else {
			stream = connection.getInputStream();
		}

		String body = null;
		try {
			if (stream == null) {
				body = connection.getResponseMessage();
			}
			else {
				body = readString(stream);
			}
		}
		finally {
			FileUtil.closeStream(stream);
		}

		return new Response(code, body);
	}

	private String readString(InputStream stream)
	throws IOException {

		if (stream == null) {
			throw new IOException("The input stream was null");
		}

		StringBuilder sb = new StringBuilder();

		try {
			int next;
			while ((next = stream.read()) != -1) {
				sb.append((char) next);
			}
		}
		catch (IOException e) {
			throw new IOException("Unable to read from the input stream", e);
		}
		finally {
			FileUtil.closeStream(stream);
		}

		return sb.toString();
	}

	private class Response {

		private int code;

		private String body;

		public Response(int code, String body) {
			this.code = code;
			this.body = body;
		}

		public int getCode() {
			return code;
		}

		public String getBody() {
			return body;
		}

	}

}
