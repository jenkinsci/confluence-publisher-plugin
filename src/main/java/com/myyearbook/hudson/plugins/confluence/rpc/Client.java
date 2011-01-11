package com.myyearbook.hudson.plugins.confluence.rpc;

import java.net.MalformedURLException;
import java.util.logging.Logger;

import org.codehaus.swizzle.confluence.Confluence;

import com.myyearbook.hudson.plugins.confluence.Util;

public class Client extends Confluence {
    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    protected Client(String url) throws MalformedURLException {
	super(url);
    }

    public static Client getInstance(String url) throws MalformedURLException {
	String rpcUrl;
	rpcUrl = Util.confluenceUrlToXmlRpcUrl(url);
	return new Client(rpcUrl);
    }
}