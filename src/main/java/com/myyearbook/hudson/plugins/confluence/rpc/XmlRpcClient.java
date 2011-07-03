
package com.myyearbook.hudson.plugins.confluence.rpc;

import hudson.plugins.confluence.soap.ConfluenceSoapService;
import hudson.plugins.confluence.soap.ConfluenceSoapServiceServiceLocator;

import java.rmi.RemoteException;

import javax.xml.rpc.ServiceException;

public class XmlRpcClient {
    protected XmlRpcClient(String url) {
    }

    public static ConfluenceSoapService getInstance(String url) throws RemoteException {
        try {
            final ConfluenceSoapServiceServiceLocator locator = new ConfluenceSoapServiceServiceLocator();
            locator.setConfluenceserviceV1EndpointAddress(url);
            return locator.getConfluenceserviceV1();
        } catch (ServiceException e) {
            throw new RemoteException("Failed to create SOAP Client", e);
        }
    }
}
