package org.eclipse.jetty.servlet;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.Servlet;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.SelectChannelConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.component.AggregateLifeCycle;
import org.eclipse.jetty.util.resource.Resource;

public class ServletTester extends AggregateLifeCycle
{
    private final Server _server=new Server();
    private final LocalConnector _connector=new LocalConnector(_server);
    private final ServletContextHandler _context;
    public void setVirtualHosts(String[] vhosts)
    {
        _context.setVirtualHosts(vhosts);
    }

    public void addVirtualHosts(String[] virtualHosts)
    {
        _context.addVirtualHosts(virtualHosts);
    }

    public ServletHolder addServlet(String className, String pathSpec)
    {
        return _context.addServlet(className,pathSpec);
    }

    public ServletHolder addServlet(Class<? extends Servlet> servlet, String pathSpec)
    {
        return _context.addServlet(servlet,pathSpec);
    }

    public void addServlet(ServletHolder servlet, String pathSpec)
    {
        _context.addServlet(servlet,pathSpec);
    }

    public void addFilter(FilterHolder holder, String pathSpec, EnumSet<DispatcherType> dispatches)
    {
        _context.addFilter(holder,pathSpec,dispatches);
    }

    public FilterHolder addFilter(Class<? extends Filter> filterClass, String pathSpec, EnumSet<DispatcherType> dispatches)
    {
        return _context.addFilter(filterClass,pathSpec,dispatches);
    }

    public FilterHolder addFilter(String filterClass, String pathSpec, EnumSet<DispatcherType> dispatches)
    {
        return _context.addFilter(filterClass,pathSpec,dispatches);
    }

    public Object getAttribute(String name)
    {
        return _context.getAttribute(name);
    }

    public Enumeration getAttributeNames()
    {
        return _context.getAttributeNames();
    }

    public Attributes getAttributes()
    {
        return _context.getAttributes();
    }

    public String getContextPath()
    {
        return _context.getContextPath();
    }

    public String getInitParameter(String name)
    {
        return _context.getInitParameter(name);
    }

    public String setInitParameter(String name, String value)
    {
        return _context.setInitParameter(name,value);
    }

    public Enumeration getInitParameterNames()
    {
        return _context.getInitParameterNames();
    }

    public Map<String, String> getInitParams()
    {
        return _context.getInitParams();
    }

    public void removeAttribute(String name)
    {
        _context.removeAttribute(name);
    }

    public void setAttribute(String name, Object value)
    {
        _context.setAttribute(name,value);
    }

    public void setContextPath(String contextPath)
    {
        _context.setContextPath(contextPath);
    }

    public Resource getBaseResource()
    {
        return _context.getBaseResource();
    }

    public String getResourceBase()
    {
        return _context.getResourceBase();
    }

    public void setResourceBase(String resourceBase)
    {
        _context.setResourceBase(resourceBase);
    }

    private final ServletHandler _handler;

    public ServletTester()
    {
        this("/",ServletContextHandler.SECURITY|ServletContextHandler.SESSIONS);
    }
    
    public ServletTester(String ctxPath)
    {
        this(ctxPath,ServletContextHandler.SECURITY|ServletContextHandler.SESSIONS);
    }
    
    public ServletTester(String contextPath,int options)
    {
        _context=new ServletContextHandler(_server,contextPath,options);
        _handler=_context.getServletHandler();
        _server.setConnectors(new Connector[]{_connector});
        addBean(_server);
    }
    
    public ServletContextHandler getContext()
    {
        return _context;
    }

    public String getResponses(String request) throws Exception
    {
        return _connector.getResponses(request);
    }
    
    public ByteBuffer getResponses(ByteBuffer request) throws Exception
    {
        return _connector.getResponses(request);
    }
    
    /* ------------------------------------------------------------ */
    /** Create a port based connector.
     * This methods adds a port connector to the server
     * @return A URL to access the server via the connector.
     * @throws Exception
     */
    public String createConnector(boolean localhost) throws Exception
    {        
        SelectChannelConnector connector = new SelectChannelConnector(_server);
        if (localhost)
            connector.setHost("127.0.0.1");
        _server.addConnector(connector);
        if (_server.isStarted())
            connector.start();
        else
            connector.open();

        return "http://"+(localhost?"127.0.0.1":
            InetAddress.getLocalHost().getHostAddress()
        )+":"+connector.getLocalPort();
    }

    public LocalConnector createLocalConnector()
    {
        LocalConnector connector = new LocalConnector(_server);
        _server.addConnector(connector);
        return connector;
    }
    
    
    
}