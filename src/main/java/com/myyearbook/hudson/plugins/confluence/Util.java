package com.myyearbook.hudson.plugins.confluence;

import java.net.URI;
import java.util.logging.Logger;

public class Util {
    private static final Logger LOGGER = Logger.getLogger(Util.class.getName());
    private static final String XML_RPC_URL_PATH = "/rpc/xmlrpc";
    private static final String SOAP_URL_PATH = "/rpc/soap-axis/confluenceservice-v1";

    public static String confluenceUrlToXmlRpcUrl(String url) {
	return URI.create(url).resolve(XML_RPC_URL_PATH).toString();
    }

    public static String confluenceUrlToSoapUrl(String url) {
	return URI.create(url).resolve(SOAP_URL_PATH).toString();
    }
}
