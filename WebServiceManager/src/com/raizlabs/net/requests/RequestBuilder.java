package com.raizlabs.net.requests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.message.BasicNameValuePair;

import com.raizlabs.baseutils.IOUtils;
import com.raizlabs.baseutils.Logger;
import com.raizlabs.events.ProgressListener;
import com.raizlabs.net.HttpMethod;
import com.raizlabs.net.ProgressInputStreamEntity;

/**
 * Builder class which allows for the construction of a request. This class
 * abstracts the implementation and can build either an {@link HttpURLConnection}
 * or an {@link HttpUriRequest} to represent and execute the given request.
 * 
 * @author Dylan James
 *
 */
public class RequestBuilder {
	private URI uri;
	private HttpMethod method;
	private LinkedHashMap<String, String> params;
	private LinkedHashMap<String, String> headers;
	private UsernamePasswordCredentials basicAuthCredentials;

	/**
	 * Constructs a {@link RequestBuilder} using the given {@link HttpMethod}
	 * and pointing to the given url.
	 * @param method The {@link HttpMethod} to use for the request.
	 * @param url The url the request targets.
	 */
	public RequestBuilder(HttpMethod method, String url) {
		this(method, URI.create(url));
	}

	/**
	 * Constructs a {@link RequestBuilder} using the given {@link HttpMethod}
	 * and pointing to the given {@link URI}.
	 * @param method The {@link HttpMethod} to use for the request.
	 * @param uri The {@link URI} the request targets.
	 */
	public RequestBuilder(HttpMethod method, URI uri) {
		this.method = method;
		this.uri = uri;
		this.params = new LinkedHashMap<String, String>();
		this.headers = new LinkedHashMap<String, String>();
	}	


	/**
	 * Sets the target URL for this {@link RequestBuilder}.
	 * @param url The url the request should target.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder setURL(String url) {
		return setURI(URI.create(url));
	}

	/**
	 * Sets the target {@link URI} for this {@link RequestBuilder}.
	 * @param uri the {@link URI} the request targets.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder setURI(URI uri) {
		this.uri = uri;
		return this;
	}
	
	/**
	 * Adds a parameter to this request.
	 * @param key The parameter key.
	 * @param value The parameter value.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder addParam(String key, String value) {
		params.put(key, value);
		return this;
	}
	
	/**
	 * Adds a parameter to the request if the value is not null.  Note that this method
	 * will still add an empty string value to the request.
	 * @param key The parameter key.
	 * @param value The parameter value.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder addParamIfNotNull(String key, String value) {
		if (value != null) {
			addParam(key, value);
		}
		return this;
	}

	/**
	 * Adds a {@link Collection} of {@link NameValuePair} as parameters to this
	 * request. Parameters are added in iteration order.
	 * @param params The collection of {@link NameValuePair} to add.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder addParams(Collection<NameValuePair> params) {
		putEntries(params, this.params);
		return this;
	}

	/**
	 * Adds a {@link Map} of parameter key value pairs as parameters of this 
	 * request. Parameters are added in iteration order.
	 * @param params The {@link Map} of parameters.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder addParams(Map<String, String> params) {
		putEntries(params, this.params);
		return this;
	}

	/**
	 * Adds a header to this request with the given name and value.
	 * @param name The name of the header.
	 * @param value The value for the header.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder addHeader(String name, String value) {
		headers.put(name, value);
		return this;
	}

	/**
	 * Adds a {@link Collection} of {@link NameValuePair} of headers to
	 * add to this request. Headers are added in iteration order.
	 * @param headers The {@link Collection} of parameters to add.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder addHeaders(Collection<NameValuePair> headers) {
		putEntries(headers, this.headers);
		return this;
	}

	/**
	 * Adds a {@link Map} of key value pairs as headers to this request.
	 * Headers are added in iteration order.
	 * @param headers The {@link Map} of header key value pairs to add.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder addHeaders(Map<String, String> headers) {
		putEntries(headers, this.headers);
		return this;
	}

	/**
	 * Adds basic authentication to this request.
	 * @param user The username to use for basic auth.
	 * @param pass The password to use for basic auth.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder setBasicAuth(String user, String pass) {
		basicAuthCredentials = new UsernamePasswordCredentials(user, pass);
		return this;
	}
	
	
	private InputStream inputStream;
	private long inputStreamLength;
	private ProgressListener inputStreamProgressListener;
	private int inputStreamProgressUpdateInterval = 128;
	/**
	 * Sets the interval for which input stream progress updates will be sent.
	 * Setting this low will result in more frequent updates which will slightly
	 * reduce performance, while setting this high may result in too infrequent updates.
	 * @param interval The interval, in bytes.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder setInputStreamProgressUpdateInterval(int interval) {
		this.inputStreamProgressUpdateInterval = interval;
		return this;
	}
	
	/**
	 * Sets the {@link InputStream} to be used as the body of the request.
	 * <br><br>
	 * @see #setInputStreamProgressUpdateInterval(int)
	 * @see #setFileInput(File, ProgressListener)
	 * @param input The {@link InputStream} to write into the body.
	 * @param length The length of the {@link InputStream}. May send negative
	 * if unknown, though the actual request may not support this.
	 * @param progressListener The {@link ProgressListener} which will be called
	 * periodically to be notified of the progress.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder setInputStream(InputStream input, long length, ProgressListener progressListener) {
		this.inputStream = input;
		this.inputStreamLength = length;
		this.inputStreamProgressListener = progressListener;
		return this;
	}
	
	/**
	 * Sets a {@link File} to be used as the body of the request.
	 * <br><br>
	 * @see #setInputStreamProgressUpdateInterval(int)
	 * @see #setInputStream(InputStream, long, ProgressListener)
	 * @param file The {@link File} to set as the body.
	 * @param progressListener The {@link ProgressListener} which will be called
	 * periodically to be notified of the progress.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder setFileInput(File file, ProgressListener progressListener) {
		if (file.exists()){
			try {
				setInputStream(new FileInputStream(file), file.length(), progressListener);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		return this;
	}
	
	/**
	 * Sets a string to be used as the body of the request. This will overwrite
	 * any existing input.
	 * @param string The string to write as the body.
	 * @param progressListener The {@link ProgressListener} which will be called
	 * periodically to be notified of the progress.
	 * @return This {@link RequestBuilder} object to allow for chaining of calls.
	 */
	public RequestBuilder setStringInput(String string, ProgressListener progressListener) {
		setInputStream(IOUtils.getInputStream(string), string.length(), progressListener);
		
		return this;
	}

	private void putEntries(Collection<NameValuePair> entries, Map<String, String> map) {
		for (NameValuePair entry : entries) {
			map.put(entry.getName(), entry.getValue());
		}
	}

	private void putEntries(Map<String, String> entries, Map<String, String> map) {
		for (Entry<String, String> entry : entries.entrySet()) {
			map.put(entry.getKey(), entry.getValue());
		}
	}
	
	private void writeToStream(OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		long totalRead = 0;
		long lastUpdate = 0;
		int read;
		try {
			// Loop through the whole stream and write the data
			while ((read = inputStream.read(buffer)) != -1) {
				out.write(buffer, 0, read);
				out.flush();
				totalRead += read;
				if (inputStreamProgressListener != null) {
					// If we have a listener, and we've read more than the given interval
					// since the last update, notify the listener
					if (totalRead - lastUpdate >= inputStreamProgressUpdateInterval) {
						inputStreamProgressListener.onProgressUpdate(totalRead, inputStreamLength);
						lastUpdate = totalRead;
					}
				}
			}
		} finally {
			IOUtils.safeClose(inputStream);
			IOUtils.safeClose(out);
		}
	}
	

	private List<NameValuePair> getNameValuePairs(Map<String, String> map) {
		List<NameValuePair> pairs = new LinkedList<NameValuePair>();
		for (Entry<String, String> entry : map.entrySet()) {
			pairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
		}
		return pairs;
	}

	private String getQueryString(Map<String, String> map) {
		StringBuilder queryBuilder = new StringBuilder();
		boolean first = true;
		for (Entry<String, String> entry : map.entrySet()) {
			if (!first) {
				queryBuilder.append("&");
			}
			queryBuilder.append(entry.getKey());
			queryBuilder.append("=");
			queryBuilder.append(URLEncoder.encode(entry.getValue()));

			first = false;

		}

		return queryBuilder.toString();
	}

	private String getUrl() {
		String url = uri.toString();
		// If we have params, set them on the url
		// Unless it is a post, in which case they go in the body
		if (params.size() > 0 && method != HttpMethod.Post) { 
			String queryString = "?" + getQueryString(params);
			url = String.format("%s%s", uri, queryString);
		}

		return url;
	}

	/**
	 * Gets a {@link HttpURLConnection} which can be used to execute this request.
	 * @return
	 */
	public HttpURLConnection getConnection() {
		try {
			// Get our current URL
			URL url = new URL(getUrl());
			// "Open" the connection, which really just gives us the object, doesn't
			// actually connect
			HttpURLConnection connection = (HttpURLConnection)url.openConnection();
			// Set the request method appropriately
			connection.setRequestMethod(method.getMethodName());
			
			// Add all headers
			for (Entry<String, String> entry : headers.entrySet()) {
				connection.setRequestProperty(entry.getKey(), entry.getValue());
			}

			// Add any basic auth
			if (basicAuthCredentials != null) {
				Header authHeader = BasicScheme.authenticate(basicAuthCredentials, Charset.defaultCharset().name(), false);
				connection.setRequestProperty(authHeader.getName(), authHeader.getValue());
			}

			// If we have params and this is a post, we need to do output
			// but they will be written later
			if (params.size() > 0 && method == HttpMethod.Post) {
				connection.setDoOutput(true);
			}
			
			// If we have an input stream, set the content length and indicate
			// that we will be doing output
			if (inputStream != null) {
				connection.setRequestProperty("Content-Length", Long.toString(inputStreamLength));
				connection.setDoOutput(true);
				// Try to set the chunked size, but this doesn't work in HttpsUrlConnections
				// due to bugs in Android URLConnection logic. Supposedly fixed in 4.1
				connection.setChunkedStreamingMode(8192);
			}
			
			return connection;

		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}
	
	/**
	 * Called once the connection has been established. Should be called after
	 * {@link #getConnection()} to allow adding the rest of the data.
	 * @param connection The opened connection
	 */
	public void onConnected(HttpURLConnection connection) {
		// If we have params and this is a put, we need to write them here
		if (params.size() > 0 && method == HttpMethod.Post) {
			// Convert the params to a query string, and write it to the body.
			String query = getQueryString(params);
			try {
				connection.getOutputStream().write(query.getBytes());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// If we have an input stream, we need to write it to the body
		if (inputStream != null) {
			try {
				OutputStream out = connection.getOutputStream();
				writeToStream(out);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Gets an {@link HttpUriRequest} which can be used to execute this request.
	 * @return
	 */
	public HttpUriRequest getRequest() {
		// Get the base request from the current method
		HttpRequestBase request = method.createRequest();
		// Set the uri to our url
		request.setURI(URI.create(getUrl()));

		// Add all headers
		for (Entry<String, String> entry : headers.entrySet()) {
			request.addHeader(entry.getKey(), entry.getValue());
		}

		// Add any basic auth
		if (basicAuthCredentials != null) {
			try {
				request.addHeader(new BasicScheme().authenticate(basicAuthCredentials, request));
			} catch (AuthenticationException e) {
				Logger.e(getClass().getName(), "Basic authentication on request threw Authentication Exception: " + e.getMessage());
				return null;
			}
		}

		// If we have parameters and this is a post, we need to add
		// the parameters to the body
		if (params.size() > 0 && method == HttpMethod.Post) {
			try {
				((HttpEntityEnclosingRequest)request).setEntity(new UrlEncodedFormEntity(getNameValuePairs(params)));
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			if (inputStream != null && request instanceof HttpEntityEnclosingRequestBase) {
				Logger.w(getClass().getName(), "Both post params and an input stream were declared in a request. The params will be overwritten.");
			}
		}
		
		// If we have an input stream and this request supports an entity, add it.
		if (inputStream != null && request instanceof HttpEntityEnclosingRequestBase) {
			// Use a progress input stream entity which notifies the progress listener
			ProgressInputStreamEntity entity = 
					new ProgressInputStreamEntity(inputStream, inputStreamLength, 
							inputStreamProgressListener, inputStreamProgressUpdateInterval);
			((HttpEntityEnclosingRequestBase)request).setEntity(entity);
		}

		return request;
	}
}
