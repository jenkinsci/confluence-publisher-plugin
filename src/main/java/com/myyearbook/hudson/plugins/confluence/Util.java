package com.myyearbook.hudson.plugins.confluence;

import java.net.URI;
import java.util.logging.Logger;

/**
 * Utility methods
 *
 * @author Joe Hansche <jhansche@myyearbook.com>
 */
public class Util {
    private static final Logger LOGGER = Logger.getLogger(Util.class.getName());

    /** Relative path to resolve the XmlRpc endpoint URL */
    private static final String XML_RPC_URL_PATH = "/rpc/xmlrpc";

    /** Relative path to resolve the SOAP endpoint URL */
    private static final String SOAP_URL_PATH = "/rpc/soap-axis/confluenceservice-v1";

    /**
     * Convert a generic Confluence URL into the XmlRpc endpoint URL
     *
     * @param url
     * @return
     * @see #XML_RPC_URL_PATH
     */
    public static String confluenceUrlToXmlRpcUrl(String url) {
	URI uri = URI.create(url);
	return uri.resolve(uri.getPath() + XML_RPC_URL_PATH).normalize().toString();
    }

    /**
     * Convert a generic Confluence URL into the SOAP endpoint URL
     *
     * @param url
     * @return
     * @see #SOAP_URL_PATH
     */
    public static String confluenceUrlToSoapUrl(String url) {
	URI uri = URI.create(url);
	return uri.resolve(uri.getPath() + SOAP_URL_PATH).normalize().toString();
    }
}
