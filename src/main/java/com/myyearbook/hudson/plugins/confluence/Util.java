/*
 * Copyright 2011-2012 MeetMe, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.myyearbook.hudson.plugins.confluence;

import java.net.URI;

/**
 * Utility methods
 *
 * @author Joe Hansche jhansche@myyearbook.com
 */
public class Util {
    /** Relative path to resolve the XmlRpc endpoint URL */
    private static final String XML_RPC_URL_PATH = "rpc/xmlrpc";

    /** Relative path to resolve the SOAP endpoint URL */
    private static final String SOAP_URL_PATH = "rpc/soap-axis/confluenceservice-v1";

    /** Relative path to resolve the SOAP v2 endpoint URL */
    private static final String SOAP_V2_URL_PATH = "rpc/soap-axis/confluenceservice-v2";

    /**
     * Convert a generic Confluence URL into the XmlRpc endpoint URL
     *
     * @param url
     * @return
     * @see #XML_RPC_URL_PATH
     */
    public static String confluenceUrlToXmlRpcUrl(String url) {
        URI uri = URI.create(url);
        return uri.resolve(XML_RPC_URL_PATH).normalize().toString();
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
        return uri.resolve(SOAP_URL_PATH).normalize().toString();
    }

    public static String confluenceUrlToSoapV2Url(String url) {
        URI uri = URI.create(url);
        return uri.resolve(SOAP_V2_URL_PATH).normalize().toString();
    }
}
