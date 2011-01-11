package com.myyearbook.hudson.plugins.confluence.rpc;

import hudson.plugins.jira.soap.ConfluenceSoapService;
import hudson.plugins.jira.soap.ConfluenceSoapServiceServiceLocator;

import java.rmi.RemoteException;

import javax.xml.rpc.ServiceException;

import com.myyearbook.hudson.plugins.confluence.Util;

public class XmlRpcClient {
    protected XmlRpcClient(String url) {
    }

    public static ConfluenceSoapService getInstance(String url) throws RemoteException {
	try {
	    String rpcUrl = Util.confluenceUrlToSoapUrl(url);
	    final ConfluenceSoapServiceServiceLocator locator = new ConfluenceSoapServiceServiceLocator();
	    locator.setConfluenceserviceV1EndpointAddress(rpcUrl);
	    return locator.getConfluenceserviceV1();
	} catch (ServiceException e) {
	    e.printStackTrace();
	    throw new RemoteException("Failed to create SOAP Client", e);
	}
    }
}