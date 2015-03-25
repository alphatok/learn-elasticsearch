package com.ctrip.esdemo.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpConnectionParamBean;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HttpClientManager {
	private static final Logger logger = LoggerFactory.getLogger(HttpClientManager.class);

	private PoolingClientConnectionManager cm = new PoolingClientConnectionManager();
	private DefaultHttpClient httpClient;

	private static final String defaultCharsetStr = "UTF-8";
	private static final Charset defaultCharset = Charset.forName(defaultCharsetStr);
	private static final String JsonContentType = "application/json; charset=UTF-8";

	private static final String ENCODING_GZIP = "gzip";
	
	private static final Integer BUFFER_SIZE = 8096;
	
	private ScheduledExecutorService idleConnectionCloseExecutor = Executors
			.newSingleThreadScheduledExecutor();

	public HttpClientManager(int connectionTimeOut, int soTimeOut) {
		HttpParams params = new SyncBasicHttpParams();
		DefaultHttpClient.setDefaultHttpParams(params);
		HttpConnectionParamBean paramsBean = new HttpConnectionParamBean(params);
		paramsBean.setConnectionTimeout(connectionTimeOut);
		paramsBean.setSoTimeout(soTimeOut);

		httpClient = new DefaultHttpClient(cm, params);
		httpClient.setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy() {
			@Override
			public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
		        long keepalive = super.getKeepAliveDuration(response, context);		        
		        if( keepalive == -1 ) {
		        	keepalive = 5000;
		        }
		        return keepalive;
		    }
		});
		httpClient.setReuseStrategy(new DefaultConnectionReuseStrategy(){		
			@Override
			public boolean keepAlive(final HttpResponse response,
                    final HttpContext context) {
				boolean keekAlive = false;
				if( HttpStatus.SC_OK == response.getStatusLine().getStatusCode() ) {
					keekAlive = super.keepAlive(response, context);
				}
				return keekAlive;
			}			
		});
		
		java.security.Security.setProperty("networkaddress.cache.ttl", "10");
	}

	public void closeIdleStart() {
		idleConnectionCloseExecutor.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				logger.trace("start to close expire and idle connections");
				cm.closeExpiredConnections();
				cm.closeIdleConnections(300, TimeUnit.SECONDS);
			}
		}, 30, 30, TimeUnit.SECONDS);
		logger.info("HttpManager close idel every 30 seconds");
	}

	public void setMaxTotal(int maxTotal) {
		cm.setMaxTotal(maxTotal);
		cm.setDefaultMaxPerRoute(maxTotal);
	}

	public String getStringExec(URI uri, String token) {
		HttpGet request = new HttpGet(uri);
		addToken(request, token);
		try {
			HttpResponse response = httpClient.execute(request);
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				return readHttpEntity(entity, response.getAllHeaders(), defaultCharset);
			} else {
				return null;
			}
		} catch (IOException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} finally {
			request.abort();
		}
	}

	public <T> List<T> getJsonListExec(URI uri, String token, Class<T> clsT) {
		HttpGet request = new HttpGet(uri);
		addToken(request, token);
		try {
			HttpResponse response = httpClient.execute(request);
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream instream = entity.getContent();
				List<T> list = JsonUtil.parserJsonList(instream, clsT);
				return list;
			} else {
				return null;
			}
		} catch (IOException e) {
			throw new RuntimeException("http get json list error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http get json list error. url: " + uri, e);
		} finally {
			request.abort();
		}
	}

	public <T> T getJsonExec(URI uri, String token, Class<T> cls) {
		logger.trace("getJsonExec {}", uri);
		HttpGet request = new HttpGet(uri);
		addToken(request, token);
		try {
			HttpResponse response = httpClient.execute(request);
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream instream = entity.getContent();
				T t = JsonUtil.parserJson(instream, cls);
				return t;
			} else {
				return null;
			}
		} catch (IOException e) {
			throw new RuntimeException("http get json error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http get json error. url: " + uri, e);
		} finally {
			request.abort();
		}
	}

	public boolean getExistExec(URI uri, String token) {
		HttpGet request = new HttpGet(uri);
		addToken(request, token);
		try {
			HttpResponse response = httpClient.execute(request);

			int status = response.getStatusLine().getStatusCode();

			if (status == HttpStatus.SC_NOT_FOUND) {
				return false;
			} else if (status == HttpStatus.SC_OK) {
				return true;
			} else {
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					logger.error("get error, status:{}, resonpse:{}, url:{}", new Object[] {status, EntityUtils.toString(entity, defaultCharset), uri });
				} else {
					logger.error("get error, status:{}, url:{}", status, uri);
				}
				throw new RuntimeException("http get error. url: " + uri);
			}
		} catch (IOException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} finally {
			request.abort();
		}

	}

	public <T> void postJsonExec(URI uri, String token, T obj) {
		HttpPost request = new HttpPost(uri);
		addContentType(request, JsonContentType);
		addToken(request, token);

		String json = JsonUtil.getJsonFromObject(obj);
		try {
			StringEntity myEntity = new StringEntity(json, defaultCharsetStr);
			request.setEntity(myEntity);
			HttpResponse response = httpClient.execute(request);

			int status = response.getStatusLine().getStatusCode();
			HttpEntity entity = response.getEntity();

			if (status >= 400) {
				if (entity != null) {
					logger.error("post error, status:{}, resonpse:{}, url:{}",new Object[] {status, EntityUtils.toString(entity, defaultCharset), uri });
				} else {
					logger.error("post error, status:{}, url:{}", status, uri);
				}
				throw new RuntimeException("http post error. url: " + uri);
			}
		} catch (IOException e) {
			throw new RuntimeException("http post error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http post error. url: " + uri, e);
		} finally {
			request.abort();
		}
	}
	
	public <T> T postImageGetJsonExec(String uri, InputStream in, Class<T> clsT) {
		HttpPost request = new HttpPost(uri);
		
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			int count;
			byte data[] = new byte[BUFFER_SIZE];
			while ((count = in.read(data, 0, BUFFER_SIZE)) != -1) {
				out.write(data, 0, count);
			}

			MultipartEntity multiEntity = new MultipartEntity();
			ByteArrayBody fileBody = new ByteArrayBody(out.toByteArray(), "screenshot.jpg");
			multiEntity.addPart("upload", fileBody);
			request.setEntity(multiEntity);

			HttpResponse response = httpClient.execute(request);
			int status = response.getStatusLine().getStatusCode();
			HttpEntity entity = response.getEntity();
			if (HttpStatus.SC_OK == status) {
				return JsonUtil.parserJson(entity.getContent(), clsT);
			} else {
				if (entity != null) {
					logger.error("post error, status:{}, resonpse:{}, url:{}", new Object[] {
							status, EntityUtils.toString(entity, defaultCharset), uri });
				} else {
					logger.error("post error, status:{}, url:{}", status, uri);
				}
				throw new RuntimeException("http post error. url: " + uri);
			}
		} catch (IOException e) {
			throw new RuntimeException("http post error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http post error. url: " + uri, e);
		} finally {
			request.abort();
		}
	}

	public void deleteExec(URI uri, String token) {
		logger.trace("deleteExec {}", uri);
		HttpDelete request = new HttpDelete(uri);
		addToken(request, token);
		try {
			HttpResponse response = httpClient.execute(request);
			int status = response.getStatusLine().getStatusCode();
			HttpEntity entity = response.getEntity();

			if (status >= 400) {
				if (entity != null) {
					logger.error("delete error, status:{}, resonpse:{}, url:{}", new Object[] { status, EntityUtils.toString(entity, defaultCharset), uri });
				} else {
					logger.error("delete error, status:{}, url:{}", status, uri);
				}
				throw new RuntimeException("http delete error. url: " + uri);
			}
		} catch (IOException e) {
			throw new RuntimeException("http delete error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http delete error. url: " + uri, e);
		} finally {
			request.abort();
		}

	}

	private void addContentType(HttpRequestBase request, String contentType) {
		request.addHeader("Content-Type", contentType);
	}

	private void addToken(HttpRequestBase request, String token) {
		if (!StringUtil.isEmpty(token)) {
			try {
				request.addHeader(new BasicHeader("Authorization", "tk=" + URLEncoder.encode(token, "UTF-8")));
			} catch (UnsupportedEncodingException e) {
				logger.error("add token error. ", e);
			}
		}
	}

	public void destory() {
		logger.trace("destory");
		idleConnectionCloseExecutor.shutdown();
		cm.shutdown();
	}

	public InputStream getInputStreamByURI(URI uri) {
		HttpGet request = new HttpGet(uri);
		try {
			HttpResponse response = httpClient.execute(request);
			HttpEntity entity = response.getEntity();
			
			int status = response.getStatusLine().getStatusCode();
			
			if ( status == HttpStatus.SC_OK && entity != null) {
				return entity.getContent();
			} else {
				try {
					if (entity != null) {
						logger.error("getInputStreamByURI error, status:{}, resonpse:{}, url:{}", new Object[] { status, EntityUtils.toString(entity, defaultCharset), uri });
						return null;
					} else {
						logger.error("getInputStreamByURI error, status:{}, url:{}", new Object[] {response.getStatusLine().getStatusCode(), uri });
						return null;
					}
				} finally {
					request.abort();
				}
			}
		} catch (IOException e) {
			request.abort();
			throw new RuntimeException(uri.toString() + " get error");
		}
	}

	public InputStream getInputStreamByURL(String url) {
		try {
			logger.debug("getInputStreamByURL, url:{}", url);
			URI uri = new URI(url);
			return getInputStreamByURI(uri);
		} catch (URISyntaxException e) {
			throw new RuntimeException(url + " get error", e);
		}
	}

	public String execGetRequestWithContent(URI uri) {
		return execGetRequestWithContent(uri, null);
	}

	public String execGetRequestWithContent(URI uri, String cookie) {
		HttpGet request = new HttpGet(uri);
		if (!StringUtil.isEmpty(cookie)) {
			request.addHeader(new BasicHeader("Cookie", cookie));
		}

		try {
			HttpResponse response = httpClient.execute(request);

			int status = response.getStatusLine().getStatusCode();
			if (status == HttpStatus.SC_OK) {
				HttpEntity entity = response.getEntity();
				return readHttpEntity(entity, response.getAllHeaders(), defaultCharset);
			} else {
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					logger.error("get error, status:{}, resonpse:{}, url:{}", new Object[] {status, EntityUtils.toString(entity, defaultCharset), uri });
				} else {
					logger.error("get error, status:{}, url:{}", status, uri);
				}
				throw new RuntimeException("http get error. url: " + uri);
			}
		} catch (IOException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} finally {
			request.abort();
		}
	}
	
	private String readHttpEntity(HttpEntity entity, Header[] headers, Charset defaultCharset) throws IOException{
		InputStream in = null;
		ByteArrayOutputStream buffer = null;
		try {
			in = entity.getContent();
			buffer = new ByteArrayOutputStream();
			byte data[] = new byte[BUFFER_SIZE];
			int count;
			Header encodingHeader = entity.getContentEncoding();
			if (encodingHeader != null && ENCODING_GZIP.equalsIgnoreCase(encodingHeader.getValue())){
				buffer = new ByteArrayOutputStream();
				new GzipDecompressingEntity(entity).writeTo(buffer);
			}else {
				while ((count = in.read(data, 0, BUFFER_SIZE)) != -1) {
					buffer.write(data, 0, count);
				}
			}
			
			return new String(buffer.toByteArray(), defaultCharset);
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			if (null != in){
				in.close();
			}
			if (null != buffer){
				buffer.close();
			}
		}
		
		return "";
	}

	public String execGetRequestWithParams(URI uri, Map<String, String> params) {
		Map<String, String> header = Collections.emptyMap();
		return execGetRequestWithParamsAndHeaders(uri, params, header);
	}
	
	public String execGetRequestWithParamsAndHeaders(URI uri, Map<String, String> params, Map<String, String> heads) {
		HttpGet request = new HttpGet(uri);
		if (null != params) {
			for (Entry<String, String> entry : params.entrySet()) {
				request.getParams().setParameter(entry.getKey(), entry.getValue());
			}
		}
		
		if( heads != null ) {
			for (Entry<String, String> entry : heads.entrySet()) {
				request.addHeader(entry.getKey(), entry.getValue());
			}
		}
		
		try {
			HttpResponse response = httpClient.execute(request);
			int status = response.getStatusLine().getStatusCode();
			if (status == HttpStatus.SC_OK) {
				HttpEntity entity = response.getEntity();
				return readHttpEntity(entity, response.getAllHeaders(), defaultCharset);
			} else {
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					logger.error("get error, status:{}, resonpse:{}, url:{}", new Object[] {status, EntityUtils.toString(entity, defaultCharset), uri });
				} else {
					logger.error("get error, status:{}, url:{}", status, uri);
				}
				throw new RuntimeException("http get error. url: " + uri);
			}
		} catch (IOException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} finally {
			request.abort();
		}
	}

	public String execGetRequestWithContent(String url) {
		try {
			URI uri = new URI(url);
			return execGetRequestWithContent(uri);
		} catch (URISyntaxException e) {
			throw new RuntimeException(url + " get error", e);
		}
	}

	public String execGetRequestWithContent(String url, String cookie) {
		try {
			URI uri = new URI(url);
			return execGetRequestWithContent(uri, cookie);
		} catch (URISyntaxException e) {
			throw new RuntimeException(url + " get error", e);
		}
	}

	public boolean execGetRequest(URI uri) {
		HttpGet request = new HttpGet(uri);
		try {
			HttpResponse response = httpClient.execute(request);

			int status = response.getStatusLine().getStatusCode();

			if (status == HttpStatus.SC_OK) {
				return true;
			} else {
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					logger.error("get error, status:{}, resonpse:{}, url:{}", new Object[] {status, EntityUtils.toString(entity, defaultCharset), uri });
				} else {
					logger.error("get error, status:{}, url:{}", status, uri);
				}
				throw new RuntimeException("http get error. url: " + uri);
			}
		} catch (IOException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} finally {
			request.abort();
		}
	}

	public boolean execGetRequest(String url) {
		try {
			URI uri = new URI(url);
			return execGetRequest(uri);
		} catch (URISyntaxException e) {
			throw new RuntimeException(url + " get error", e);
		}
	}

	public String execGetRequestWithHeader(String url, Map<String,String> header) {
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			throw new RuntimeException(url + " get error", e);
		}
		Map<String, String> param = Collections.emptyMap();
		return execGetRequestWithParamsAndHeaders(uri, param, header);
	}

	public <T> String execPostJsonRequest(String url, T obj) {
		try {
			URI uri = new URI(url);
			return execPostJsonRequest(uri, obj);
		} catch (URISyntaxException e) {
			throw new RuntimeException(url + " get error", e);
		}
	}

	public <T> String execPostJsonRequest(URI uri, T obj) {
		Map<String, String> headers = Collections.emptyMap();
		Map<String, String> params = Collections.emptyMap();
		return execPostRequestWithParamsAndHeaders(uri, params, headers, obj);
	}
	
	public <T> String execPostRequest(URI uri, T obj) {
		Map<String, String> headers = Collections.emptyMap();
		Map<String, String> params = Collections.emptyMap();
		return execPostRequestWithParamsAndHeaders(uri, params, headers, obj);
	}
	
	public <T> String execPostRequestWithParams(URI uri, Map<String, String> params, T obj) {
		Map<String, String> headers = Collections.emptyMap();
		return execPostRequestWithParamsAndHeaders(uri, params, headers, obj);
	}
	
	public <T> String execPostRequestWithParamsAndHeaders(URI uri, Map<String, String> params, Map<String, String> headers, T obj) {
		HttpPost request = new HttpPost(uri);
		addContentType(request, JsonContentType);
		
		if (params != null && !params.isEmpty()) {
			for (Entry<String, String> entry : params.entrySet()) {
				request.getParams().setParameter(entry.getKey(), entry.getValue());
			}
		}
		
		if(headers != null && !headers.isEmpty()) {
			for (Entry<String, String> entry : headers.entrySet()) {
				request.addHeader(entry.getKey(), entry.getValue());
			}
		}

		String json = JsonUtil.getJsonFromObject(obj);
		try {
			StringEntity myEntity = new StringEntity(json, defaultCharsetStr);
			request.setEntity(myEntity);
			HttpResponse response = httpClient.execute(request);

			int status = response.getStatusLine().getStatusCode();
			HttpEntity entity = response.getEntity();
			if (status == HttpStatus.SC_OK) {
				return readHttpEntity(entity, response.getAllHeaders(), defaultCharset);
			} else {
				if (entity != null) {
					logger.error("post error, status:{}, resonpse:{}, url:{}", new Object[] {status, EntityUtils.toString(entity, defaultCharset), uri });
				} else {
					logger.error("post error, status:{}, url:{}", status, uri);
				}
				throw new RuntimeException("http post error. url: " + uri);
			}
		} catch (IOException e) {
			throw new RuntimeException("http post error. url: " + uri, e);
		} catch (RuntimeException e) {
			throw new RuntimeException("http post error. url: " + uri, e);
		} finally {
			request.abort();
		}
	}
	
	public String execDeleteRequestWithContent(String url){
		try {
			URI uri = new URI(url);
			return execDeleteRequestWithContent(uri, null);
		} catch (URISyntaxException e) {
			throw new RuntimeException(url + " get error", e);
		}
	}
	
	
	public String execDeleteRequestWithContent(URI uri, String cookie){
		HttpDelete request = new HttpDelete(uri);
		if( !StringUtil.isEmpty(cookie) ) {
			request.addHeader(new BasicHeader("Cookie", cookie));
		}
		
		try {
            HttpResponse response = httpClient.execute(request);
            
            int status = response.getStatusLine().getStatusCode();
            
            if ( status ==  HttpStatus.SC_OK){
            	HttpEntity entity = response.getEntity();
            	return readHttpEntity(entity, response.getAllHeaders(), defaultCharset);
            } else {
            	HttpEntity entity = response.getEntity(); 
            	if( entity != null ) {
					logger.error("get error, status:{}, resonpse:{}, url:{}", new Object[]{status, EntityUtils.toString(entity, defaultCharset), uri});
				} else {
					logger.error("get error, status:{}, url:{}", status, uri);
				}
				throw new RuntimeException("http get error. url: " + uri);
            }
        } catch (IOException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} catch( RuntimeException e) {
			throw new RuntimeException("http get error. url: " + uri, e);
		} finally {
			request.abort();
		}
	}
}