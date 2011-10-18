
package com.myyearbook.hudson.plugins.confluence.rpc;

import java.rmi.RemoteException;

import javax.xml.rpc.ServiceException;

public class XmlRpcClient {
    protected XmlRpcClient(String url) {
    }

    public static jenkins.plugins.confluence.soap.v1.ConfluenceSoapService getInstance(String url)
            throws RemoteException {
        try {
            final jenkins.plugins.confluence.soap.v1.ConfluenceSoapServiceServiceLocator locator = new jenkins.plugins.confluence.soap.v1.ConfluenceSoapServiceServiceLocator();
            locator.setConfluenceserviceV1EndpointAddress(url);
            return locator.getConfluenceserviceV1();
        } catch (ServiceException e) {
            throw new RemoteException("Failed to create SOAP Client", e);
        }
    }

    public static jenkins.plugins.confluence.soap.v2.ConfluenceSoapService getV2Instance(String url)
            throws RemoteException {
        try {
            final jenkins.plugins.confluence.soap.v2.ConfluenceSoapServiceServiceLocator locator = new jenkins.plugins.confluence.soap.v2.ConfluenceSoapServiceServiceLocator();
            locator.setConfluenceserviceV2EndpointAddress(url);
            return locator.getConfluenceserviceV2();
        } catch (ServiceException e) {
            throw new RemoteException("Failed to create SOAP Client", e);
        }
    }
}
