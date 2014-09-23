package com.atos.petbot.xmlrpc;

import android.util.Log;

import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.client.XmlRpcClient;
//import org.apache.xmlrpc.client.XmlRpcClientException;
import org.apache.xmlrpc.client.XmlRpcClientException;
import org.apache.xmlrpc.client.XmlRpcSunHttpTransportFactory;
import org.apache.xmlrpc.client.XmlRpcSunHttpTransport;
import org.apache.xmlrpc.client.XmlRpcTransport;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

/**
 * Created by root on 6/24/14.
 */

public class XmlRpcHttpCookieTransportFactory extends XmlRpcSunHttpTransportFactory {

	public XmlRpcHttpCookieTransportFactory(XmlRpcClient client) {
		super(client);
	}

	@Override
	public XmlRpcTransport getTransport() {

		return new XmlRpcSunHttpTransport(this.getClient()){

			private URLConnection connection;

			protected URLConnection newURLConnection(URL pURL) throws IOException {
				connection = super.newURLConnection(pURL);
				return connection;
			}

			@Override
			protected void initHttpHeaders(final XmlRpcRequest pRequest) {
				try {
					super.initHttpHeaders(pRequest);
				} catch (XmlRpcClientException e) {
					e.printStackTrace();
				}

				CookieManager manager = (CookieManager) CookieManager.getDefault();
				CookieStore cookieJar =  manager.getCookieStore();

				List<HttpCookie> cookies = null;
				cookies = cookieJar.getCookies();

				connection.setRequestProperty("Cookie", cookies.get(0).toString());
			}

		};
	}
}

