package com.pubnub.api;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;

import org.json.JSONArray;
import org.json.JSONException;

import com.pubnub.api.PubnubException;

class HttpClientCore extends HttpClient {
    private int requestTimeout = 310000;
    private int connectionTimeout = 5000;
    HttpURLConnection connection;
    protected static Logger log = new Logger(Worker.class);

    private void init() {
        HttpURLConnection.setFollowRedirects(true);
    }

    public HttpClientCore(int connectionTimeout, int requestTimeout, Hashtable headers) {
        init();
        this.setRequestTimeout(requestTimeout);
        this.setConnectionTimeout(connectionTimeout);
        this._headers = headers;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isRedirect(int rc) {
        return (rc == HttpURLConnection.HTTP_MOVED_PERM
                || rc == HttpURLConnection.HTTP_MOVED_TEMP || rc == HttpURLConnection.HTTP_SEE_OTHER);
    }

    public boolean checkResponse(int rc) {
        return (rc == HttpURLConnection.HTTP_OK || isRedirect(rc));
    }

    public boolean checkResponseSuccess(int rc) {
        return (rc == HttpURLConnection.HTTP_OK);
    }

    private static String readInput(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte bytes[] = new byte[1024];

        int n = in.read(bytes);

        while (n != -1) {
            out.write(bytes, 0, n);
            n = in.read(bytes);
        }

        return new String(out.toString());
    }

    public HttpResponse fetch(String url) throws PubnubException, SocketTimeoutException {
        return fetch(url, null);
    }

    public synchronized HttpResponse fetch(String url, Hashtable headers)
            throws PubnubException, SocketTimeoutException {
        URL urlobj = null;
        try {
            urlobj = new URL(url);
        } catch (MalformedURLException e3) {
            throw new PubnubException(PubnubError.PNERR_5006_MALFORMED_URL);
        }
        try {
            connection = (HttpURLConnection) urlobj.openConnection();
        } catch (IOException e2) {
            throw new PubnubException(PubnubError.PNERR_5008_URL_OPEN);
        }
        try {
            connection.setRequestMethod("GET");
        } catch (ProtocolException e1) {
            throw new PubnubException(PubnubError.PNERR_5009_PROTOCOL_EXCEPTION);
        }
        if (_headers != null) {
            Enumeration en = _headers.keys();
            while (en.hasMoreElements()) {
                String key = (String) en.nextElement();
                String val = (String) _headers.get(key);
                connection.addRequestProperty(key, val);
            }
        }
        if (headers != null) {
            Enumeration en = headers.keys();
            while (en.hasMoreElements()) {
                String key = (String) en.nextElement();
                String val = (String) headers.get(key);
                connection.addRequestProperty(key, val);
            }
        }
        connection.setReadTimeout(requestTimeout);
        connection.setConnectTimeout(connectionTimeout);


        try {
            connection.connect();
        }
        catch (SocketTimeoutException  e) {
            throw e;
        }
        catch (IOException e) {
            throw new PubnubException(PubnubError.PNERR_5010_CONNECT_EXCEPTION);
        }

        int rc = HttpURLConnection.HTTP_CLIENT_TIMEOUT;
        try {
            rc = connection.getResponseCode();
        } catch (IOException e) {
            throw new PubnubException(PubnubError.PNERR_5011_HTTP_RC_ERROR);
        }


        InputStream is = null;
        String encoding = connection.getContentEncoding();

        if(encoding == null || !encoding.equals("gzip")) {
            try {
                is = connection.getInputStream();
            } catch (IOException e) {
                if (rc == HttpURLConnection.HTTP_OK)
                    throw new PubnubException(PubnubError.PNERR_5012_GETINPUTSTREAM);
                is = connection.getErrorStream();
            }

        } else {
            try {
                is = new GZIPInputStream(connection.getInputStream());
            } catch (IOException e) {
                if (rc == HttpURLConnection.HTTP_OK)
                    throw new PubnubException(PubnubError.PNERR_5013_GETINPUTSTREAM);
                is = connection.getErrorStream();
            }
        }

        String page = null;
        try {
            page = readInput(is);
        } catch (IOException e) {
            throw new PubnubException(PubnubError.PNERR_5014_READINPUT);
        }

        switch (rc) {
        case HttpURLConnection.HTTP_FORBIDDEN:
            return new HttpResponse( rc, "");
        case HttpURLConnection.HTTP_BAD_REQUEST:
            try {
                JSONArray jsarr = new JSONArray(page);
                String error = jsarr.get(1).toString();
                throw new PubnubException(PubnubError.PNERR_5016_BAD_REQUEST, error);
            } catch (JSONException e) {
                throw new PubnubException(PubnubError.PNERR_5015_INVALID_JSON);
            }
        case HttpURLConnection.HTTP_BAD_GATEWAY:
            throw new PubnubException(PubnubError.PNERR_5020_BAD_GATEWAY);
        case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
            throw new PubnubException(PubnubError.PNERR_5021_CLIENT_TIMEOUT);
        case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
            throw new PubnubException(PubnubError.PNERR_5022_GATEWAY_TIMEOUT);
        case HttpURLConnection.HTTP_INTERNAL_ERROR:
            throw new PubnubException(PubnubError.PNERR_5023_INTERNAL_ERROR);
        default:
            break;
        }
        log.verbose("URL = " + url + " : RESPONSE = " + page);
        return new HttpResponse(rc, page);
    }

    public boolean isOk(int rc) {
        return (rc == HttpURLConnection.HTTP_OK);
    }

    public void shutdown() {
        if (connection != null) connection.disconnect();
    }
}
