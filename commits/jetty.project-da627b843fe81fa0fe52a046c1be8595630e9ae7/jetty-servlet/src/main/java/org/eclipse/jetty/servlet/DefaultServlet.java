// ========================================================================
// Copyright (c) 1999-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.eclipse.jetty.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpContent;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeaderValues;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.io.WriterOutputStream;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.InclusiveByteRange;
import org.eclipse.jetty.server.ResourceCache;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.nio.NIOConnector;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.MultiPartOutputStream;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;



/* ------------------------------------------------------------ */
/** The default servlet.                                                 
 * This servlet, normally mapped to /, provides the handling for static 
 * content, OPTION and TRACE methods for the context.                   
 * The following initParameters are supported, these can be set either
 * on the servlet itself or as ServletContext initParameters with a prefix
 * of org.eclipse.jetty.servlet.Default. :                          
 * <PRE>                                                                      
 *   acceptRanges     If true, range requests and responses are         
 *                    supported                                         
 *                                                                      
 *   dirAllowed       If true, directory listings are returned if no    
 *                    welcome file is found. Else 403 Forbidden.        
 *
 *   redirectWelcome  If true, welcome files are redirected rather than
 *                    forwarded to.
 *
 *   gzip             If set to true, then static content will be served as 
 *                    gzip content encoded if a matching resource is 
 *                    found ending with ".gz"
 *
 *  resourceBase      Set to replace the context resource base
 *
 *  relativeResourceBase    
 *                    Set with a pathname relative to the base of the
 *                    servlet context root. Useful for only serving static content out
 *                    of only specific subdirectories.
 * 
 *  aliases           If True, aliases of resources are allowed (eg. symbolic
 *                    links and caps variations). May bypass security constraints.
 *                    
 *  maxCacheSize      The maximum total size of the cache or 0 for no cache.
 *  maxCachedFileSize The maximum size of a file to cache
 *  maxCachedFiles    The maximum number of files to cache
 *  cacheType         Set to "bio", "nio" or "both" to determine the type resource cache. 
 *                    A bio cached buffer may be used by nio but is not as efficient as an
 *                    nio buffer.  An nio cached buffer may not be used by bio.    
 *  
 *  useFileMappedBuffer 
 *                    If set to true, it will use mapped file buffer to serve static content
 *                    when using NIO connector. Setting this value to false means that
 *                    a direct buffer will be used instead of a mapped file buffer. 
 *                    By default, this is set to true.
 *                    
 *  cacheControl      If set, all static content will have this value set as the cache-control
 *                    header.
 *                    
 * 
 * </PRE>
 *                                                                    
 *
 * 
 * 
 */
public class DefaultServlet extends HttpServlet implements ResourceFactory
{   
    private ServletContext _servletContext;
    private ContextHandler _contextHandler;
    
    private boolean _acceptRanges=true;
    private boolean _dirAllowed=true;
    private boolean _redirectWelcome=false;
    private boolean _gzip=true;
    
    private Resource _resourceBase;
    private NIOResourceCache _nioCache;
    private ResourceCache _bioCache;
    
    private MimeTypes _mimeTypes;
    private String[] _welcomes;
    private boolean _useFileMappedBuffer=false;
    private ByteArrayBuffer _cacheControl;
    private String _relativeResourceBase;
    
    
    /* ------------------------------------------------------------ */
    public void init()
        throws UnavailableException
    {
        _servletContext=getServletContext();
        ContextHandler.Context scontext=ContextHandler.getCurrentContext();
        if (scontext==null)
            _contextHandler=((ContextHandler.Context)_servletContext).getContextHandler();
        else
            _contextHandler = ContextHandler.getCurrentContext().getContextHandler();
        
        _mimeTypes = _contextHandler.getMimeTypes();
        
        _welcomes = _contextHandler.getWelcomeFiles();
        if (_welcomes==null)
            _welcomes=new String[] {"index.jsp","index.html"};
        
        _acceptRanges=getInitBoolean("acceptRanges",_acceptRanges);
        _dirAllowed=getInitBoolean("dirAllowed",_dirAllowed);
        _redirectWelcome=getInitBoolean("redirectWelcome",_redirectWelcome);
        _gzip=getInitBoolean("gzip",_gzip);
        
        String aliases=_servletContext.getInitParameter("aliases");
        if (aliases!=null)
            _contextHandler.setAliases(Boolean.parseBoolean(aliases));
        
        _useFileMappedBuffer=getInitBoolean("useFileMappedBuffer",_useFileMappedBuffer);
        
        _relativeResourceBase = getInitParameter("relativeResourceBase");
        
        String rb=getInitParameter("resourceBase");
        if (rb!=null)
        {
            if (_relativeResourceBase!=null)
                throw new  UnavailableException("resourceBase & relativeResourceBase");    
            try{_resourceBase=_contextHandler.newResource(rb);}
            catch (Exception e) 
            {
                Log.warn(Log.EXCEPTION,e);
                throw new UnavailableException(e.toString()); 
            }
        }
        
        String t=getInitParameter("cacheControl");
        if (t!=null)
            _cacheControl=new ByteArrayBuffer(t);
        
        try
        {
            String cache_type =getInitParameter("cacheType");
            int max_cache_size=getInitInt("maxCacheSize", -2);
            int max_cached_file_size=getInitInt("maxCachedFileSize", -2);
            int max_cached_files=getInitInt("maxCachedFiles", -2);

            if (cache_type==null || "nio".equals(cache_type)|| "both".equals(cache_type))
            {
                if (max_cache_size==-2 || max_cache_size>0)
                {
                    _nioCache=new NIOResourceCache(_mimeTypes);
                    _nioCache.setUseFileMappedBuffer(_useFileMappedBuffer);
                    if (max_cache_size>0)
                        _nioCache.setMaxCacheSize(max_cache_size);    
                    if (max_cached_file_size>=-1)
                        _nioCache.setMaxCachedFileSize(max_cached_file_size);    
                    if (max_cached_files>=-1)
                        _nioCache.setMaxCachedFiles(max_cached_files);
                    _nioCache.start();
                }
            }
            if ("bio".equals(cache_type)|| "both".equals(cache_type))
            {
                if (max_cache_size==-2 || max_cache_size>0)
                {
                    _bioCache=new ResourceCache(_mimeTypes);
                    if (max_cache_size>0)
                        _bioCache.setMaxCacheSize(max_cache_size);    
                    if (max_cached_file_size>=-1)
                        _bioCache.setMaxCachedFileSize(max_cached_file_size);    
                    if (max_cached_files>=-1)
                        _bioCache.setMaxCachedFiles(max_cached_files);
                    _bioCache.start();
                }
            }
            if (_nioCache==null)
                _bioCache=null;
           
        }
        catch (Exception e) 
        {
            Log.warn(Log.EXCEPTION,e);
            throw new UnavailableException(e.toString()); 
        }
        
        if (Log.isDebugEnabled()) Log.debug("resource base = "+_resourceBase);
    }

    /* ------------------------------------------------------------ */
    public String getInitParameter(String name)
    {
        String value=getServletContext().getInitParameter("org.eclipse.jetty.servlet.Default."+name);
	if (value==null)
	    value=super.getInitParameter(name);
	return value;
    }
    
    /* ------------------------------------------------------------ */
    private boolean getInitBoolean(String name, boolean dft)
    {
        String value=getInitParameter(name);
        if (value==null || value.length()==0)
            return dft;
        return (value.startsWith("t")||
                value.startsWith("T")||
                value.startsWith("y")||
                value.startsWith("Y")||
                value.startsWith("1"));
    }
    
    /* ------------------------------------------------------------ */
    private int getInitInt(String name, int dft)
    {
        String value=getInitParameter(name);
	if (value==null)
            value=getInitParameter(name);
        if (value!=null && value.length()>0)
            return Integer.parseInt(value);
        return dft;
    }
    
    /* ------------------------------------------------------------ */
    /** get Resource to serve.
     * Map a path to a resource. The default implementation calls
     * HttpContext.getResource but derived servlets may provide
     * their own mapping.
     * @param pathInContext The path to find a resource for.
     * @return The resource to serve.
     */
    public Resource getResource(String pathInContext)
    {
        Resource r=null;
        if (_relativeResourceBase!=null)
            pathInContext=URIUtil.addPaths(_relativeResourceBase,pathInContext);
        
        try
        {
            if (_resourceBase!=null)
                r = _resourceBase.addPath(pathInContext);
            else
            {
                URL u = _servletContext.getResource(pathInContext);
                r = _contextHandler.newResource(u);
            }
           
            if (Log.isDebugEnabled()) Log.debug("RESOURCE="+r);
        }
        catch (IOException e)
        {
            Log.ignore(e);
        }
        return r;
    }
    
    /* ------------------------------------------------------------ */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    	throws ServletException, IOException
    {
        String servletPath=null;
        String pathInfo=null;
        Enumeration reqRanges = null;
        Boolean included =request.getAttribute(Dispatcher.__INCLUDE_REQUEST_URI)!=null;
        if (included!=null && included.booleanValue())
        {
            servletPath=(String)request.getAttribute(Dispatcher.__INCLUDE_SERVLET_PATH);
            pathInfo=(String)request.getAttribute(Dispatcher.__INCLUDE_PATH_INFO);
            if (servletPath==null)
            {
                servletPath=request.getServletPath();
                pathInfo=request.getPathInfo();
            }
        }
        else
        {
            included=Boolean.FALSE;
            servletPath=request.getServletPath();
            pathInfo=request.getPathInfo();
            
            // Is this a range request?
            reqRanges = request.getHeaders(HttpHeaders.RANGE);
            if (reqRanges!=null && !reqRanges.hasMoreElements())
                reqRanges=null;
        }
        
        String pathInContext=URIUtil.addPaths(servletPath,pathInfo);
        boolean endsWithSlash=pathInContext.endsWith(URIUtil.SLASH);
        
        // Can we gzip this request?
        String pathInContextGz=null;
        boolean gzip=false;
        if (!included.booleanValue() && _gzip && reqRanges==null && !endsWithSlash )
        {
            String accept=request.getHeader(HttpHeaders.ACCEPT_ENCODING);
            if (accept!=null && accept.indexOf("gzip")>=0)
                gzip=true;
        }
        
        // Find the resource and content
        Resource resource=null;
        HttpContent content=null;
        
        Connector connector = HttpConnection.getCurrentConnection().getConnector();
        ResourceCache cache=(connector instanceof NIOConnector) ?_nioCache:_bioCache;
        try
        {   
            // Try gzipped content first
            if (gzip)
            {
                pathInContextGz=pathInContext+".gz";  

                if (cache==null)
                {
                    resource=getResource(pathInContextGz);
                }
                else
                {
                    content=cache.lookup(pathInContextGz,this);

                    if (content!=null)
                        resource=content.getResource();
                    else
                        resource=getResource(pathInContextGz);
                }

                if (resource==null || !resource.exists()|| resource.isDirectory())
                {
                    if (cache!=null && content==null)
                    {
                        String real_path=_servletContext.getRealPath(pathInContextGz);
                        if (real_path!=null)
                            cache.miss(pathInContextGz,_contextHandler.newResource(real_path));
                    }
                    gzip=false;
                    pathInContextGz=null;
                }
            }
        
            // find resource
            if (!gzip)
            {
                if (cache==null)
                    resource=getResource(pathInContext);
                else
                {
                    content=cache.lookup(pathInContext,this);

                    if (content!=null)
                        resource=content.getResource();
                    else
                        resource=getResource(pathInContext);
                }
            }
            
            if (Log.isDebugEnabled())
                Log.debug("resource="+resource+(content!=null?" content":""));
                        
            // Handle resource
            if (resource==null || !resource.exists())
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            else if (!resource.isDirectory())
            {   
                // ensure we have content
                if (content==null)
                    content=new UnCachedContent(resource);
                
                if (included.booleanValue() || passConditionalHeaders(request,response, resource,content))  
                {
                    if (gzip)
                    {
                       response.setHeader(HttpHeaders.CONTENT_ENCODING,"gzip");
                       String mt=_servletContext.getMimeType(pathInContext);
                       if (mt!=null)
                           response.setContentType(mt);
                    }
                    sendData(request,response,included.booleanValue(),resource,content,reqRanges);  
                }
            }
            else
            {
                String welcome=null;
                
                if (!endsWithSlash || (pathInContext.length()==1 && request.getAttribute("org.eclipse.jetty.server.nullPathInfo")!=null))
                {
                    StringBuffer buf=request.getRequestURL();
                    synchronized(buf)
                    {
                        int param=buf.lastIndexOf(";");
                        if (param<0)
                            buf.append('/');
                        else
                            buf.insert(param,'/');
                        String q=request.getQueryString();
                        if (q!=null&&q.length()!=0)
                        {
                            buf.append('?');
                            buf.append(q);
                        }
                        response.setContentLength(0);
                        response.sendRedirect(response.encodeRedirectURL(buf.toString()));
                    }
                }
                // else look for a welcome file
                else if (null!=(welcome=getWelcomeFile(resource)))
                {
                    String ipath=URIUtil.addPaths(pathInContext,welcome);
                    if (_redirectWelcome)
                    {
                        // Redirect to the index
                        response.setContentLength(0);
                        String q=request.getQueryString();
                        if (q!=null&&q.length()!=0)
                            response.sendRedirect(URIUtil.addPaths( _servletContext.getContextPath(),ipath)+"?"+q);
                        else
                            response.sendRedirect(URIUtil.addPaths( _servletContext.getContextPath(),ipath));
                    }
                    else
                    {
                        // Forward to the index
                        RequestDispatcher dispatcher=request.getRequestDispatcher(ipath);
                        if (dispatcher!=null)
                        {
                            if (included.booleanValue())
                                dispatcher.include(request,response);
                            else
                            {
                                request.setAttribute("org.eclipse.jetty.server.welcome",ipath);
                                dispatcher.forward(request,response);
                            }
                        }
                    }
                }
                else 
                {
                    content=new UnCachedContent(resource);
                    if (included.booleanValue() || passConditionalHeaders(request,response, resource,content))
                        sendDirectory(request,response,resource,pathInContext.length()>1);
                }
            }
        }
        catch(IllegalArgumentException e)
        {
            Log.warn(Log.EXCEPTION,e);
            if(!response.isCommitted())
                response.sendError(500, e.getMessage());
        }
        finally
        {
            if (content!=null)
                content.release();
            else if (resource!=null)
                resource.release();
        }
        
    }
    
    /* ------------------------------------------------------------ */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        doGet(request,response);
    }
    
    /* ------------------------------------------------------------ */
    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doTrace(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /* ------------------------------------------------------------ */
    /**
     * Finds a matching welcome file for the supplied {@link Resource}. This will be the first entry in the list of 
     * configured {@link #_welcomes welcome files} that existing within the directory referenced by the <code>Resource</code>.
     * If the resource is not a directory, or no matching file is found, then <code>null</code> is returned.
     * The list of welcome files is read from the {@link ContextHandler} for this servlet, or
     * <code>"index.jsp" , "index.html"</code> if that is <code>null</code>.
     * @param resource
     * @return The name of the matching welcome file.
     * @throws IOException
     * @throws MalformedURLException
     */
    private String getWelcomeFile(Resource resource) throws MalformedURLException, IOException
    {
        if (!resource.isDirectory() || _welcomes==null)
            return null;

        for (int i=0;i<_welcomes.length;i++)
        {
            Resource welcome=resource.addPath(_welcomes[i]);
            if (welcome.exists())
                return _welcomes[i];
        }

        return null;
    }

    /* ------------------------------------------------------------ */
    /* Check modification date headers.
     */
    protected boolean passConditionalHeaders(HttpServletRequest request,HttpServletResponse response, Resource resource, HttpContent content)
    throws IOException
    {
        try
        {
            if (!request.getMethod().equals(HttpMethods.HEAD) )
            {
                String ifms=request.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
                if (ifms!=null)
                {
                    if (content!=null)
                    {
                        Buffer mdlm=content.getLastModified();
                        if (mdlm!=null)
                        {
                            if (ifms.equals(mdlm.toString()))
                            {
                                response.reset();
                                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                                response.flushBuffer();
                                return false;
                            }
                        }
                    }
                        
                    long ifmsl=request.getDateHeader(HttpHeaders.IF_MODIFIED_SINCE);
                    if (ifmsl!=-1)
                    {
                        if (resource.lastModified()/1000 <= ifmsl/1000)
                        {
                            response.reset();
                            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                            response.flushBuffer();
                            return false;
                        }
                    }
                }

                // Parse the if[un]modified dates and compare to resource
                long date=request.getDateHeader(HttpHeaders.IF_UNMODIFIED_SINCE);
                
                if (date!=-1)
                {
                    if (resource.lastModified()/1000 > date/1000)
                    {
                        response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                        return false;
                    }
                }
                
            }
        }
        catch(IllegalArgumentException iae)
        {
            if(!response.isCommitted())
                response.sendError(400, iae.getMessage());
            throw iae;
        }
        return true;
    }
    
    
    /* ------------------------------------------------------------------- */
    protected void sendDirectory(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Resource resource,
                                 boolean parent)
    throws IOException
    {
        if (!_dirAllowed)
        {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        
        byte[] data=null;
        String base = URIUtil.addPaths(request.getRequestURI(),URIUtil.SLASH);
        String dir = resource.getListHTML(base,parent);
        if (dir==null)
        {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "No directory");
            return;
        }
        
        data=dir.getBytes("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
    }
    
    /* ------------------------------------------------------------ */
    protected void sendData(HttpServletRequest request,
                            HttpServletResponse response,
                            boolean include,
                            Resource resource,
                            HttpContent content,
                            Enumeration reqRanges)
    throws IOException
    {
        long content_length=resource.length();
        
        // Get the output stream (or writer)
        OutputStream out =null;
        try{out = response.getOutputStream();}
        catch(IllegalStateException e) {out = new WriterOutputStream(response.getWriter());}
        
        if ( reqRanges == null || !reqRanges.hasMoreElements())
        {
            //  if there were no ranges, send entire entity
            if (include)
            {
                resource.writeTo(out,0,content_length);
            }
            else
            {
                // See if a short direct method can be used?
                if (out instanceof HttpConnection.Output)
                {
                    if (_cacheControl!=null)
                    {
                        if (response instanceof Response)
                            ((Response)response).getHttpFields().put(HttpHeaders.CACHE_CONTROL_BUFFER,_cacheControl);
                        else
                            response.setHeader(HttpHeaders.CACHE_CONTROL,_cacheControl.toString());
                    }
                    ((HttpConnection.Output)out).sendContent(content);
                }
                else
                {
                    
                    // Write content normally
                    writeHeaders(response,content,content_length);
                    resource.writeTo(out,0,content_length);
                }
            }
        }
        else
        {
            // Parse the satisfiable ranges
            List ranges =InclusiveByteRange.satisfiableRanges(reqRanges,content_length);
            
            //  if there are no satisfiable ranges, send 416 response
            if (ranges==null || ranges.size()==0)
            {
                writeHeaders(response, content, content_length);
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader(HttpHeaders.CONTENT_RANGE, 
                        InclusiveByteRange.to416HeaderRangeString(content_length));
                resource.writeTo(out,0,content_length);
                return;
            }
            
            
            //  if there is only a single valid range (must be satisfiable 
            //  since were here now), send that range with a 216 response
            if ( ranges.size()== 1)
            {
                InclusiveByteRange singleSatisfiableRange =
                    (InclusiveByteRange)ranges.get(0);
                long singleLength = singleSatisfiableRange.getSize(content_length);
                writeHeaders(response,content,singleLength                     );
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader(HttpHeaders.CONTENT_RANGE, 
                        singleSatisfiableRange.toHeaderRangeString(content_length));
                resource.writeTo(out,singleSatisfiableRange.getFirst(content_length),singleLength);
                return;
            }
            
            
            //  multiple non-overlapping valid ranges cause a multipart
            //  216 response which does not require an overall 
            //  content-length header
            //
            writeHeaders(response,content,-1);
            String mimetype=content.getContentType().toString();
            MultiPartOutputStream multi = new MultiPartOutputStream(out);
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            
            // If the request has a "Request-Range" header then we need to
            // send an old style multipart/x-byteranges Content-Type. This
            // keeps Netscape and acrobat happy. This is what Apache does.
            String ctp;
            if (request.getHeader(HttpHeaders.REQUEST_RANGE)!=null)
                ctp = "multipart/x-byteranges; boundary=";
            else
                ctp = "multipart/byteranges; boundary=";
            response.setContentType(ctp+multi.getBoundary());
            
            InputStream in=resource.getInputStream();
            long pos=0;
            
            for (int i=0;i<ranges.size();i++)
            {
                InclusiveByteRange ibr = (InclusiveByteRange) ranges.get(i);
                String header=HttpHeaders.CONTENT_RANGE+": "+
                ibr.toHeaderRangeString(content_length);
                multi.startPart(mimetype,new String[]{header});
                
                long start=ibr.getFirst(content_length);
                long size=ibr.getSize(content_length);
                if (in!=null)
                {
                    // Handle non cached resource
                    if (start<pos)
                    {
                        in.close();
                        in=resource.getInputStream();
                        pos=0;
                    }
                    if (pos<start)
                    {
                        in.skip(start-pos);
                        pos=start;
                    }
                    IO.copy(in,multi,size);
                    pos+=size;
                }
                else
                    // Handle cached resource
                    (resource).writeTo(multi,start,size);
                
            }
            if (in!=null)
                in.close();
            multi.close();
        }
        return;
    }
    
    /* ------------------------------------------------------------ */
    protected void writeHeaders(HttpServletResponse response,HttpContent content,long count)
        throws IOException
    {   
        if (content.getContentType()!=null)
            response.setContentType(content.getContentType().toString());
        
        if (response instanceof Response)
        {
            Response r=(Response)response;
            HttpFields fields = r.getHttpFields();

            if (content.getLastModified()!=null)  
                fields.put(HttpHeaders.LAST_MODIFIED_BUFFER,content.getLastModified(),content.getResource().lastModified());
            else if (content.getResource()!=null)
            {
                long lml=content.getResource().lastModified();
                if (lml!=-1)
                    fields.putDateField(HttpHeaders.LAST_MODIFIED_BUFFER,lml);
            }
                
            if (count != -1)
                r.setLongContentLength(count);

            if (_acceptRanges)
                fields.put(HttpHeaders.ACCEPT_RANGES_BUFFER,HttpHeaderValues.BYTES_BUFFER);

            if (_cacheControl!=null)
                fields.put(HttpHeaders.CACHE_CONTROL_BUFFER,_cacheControl);
            
        }
        else
        {
            long lml=content.getResource().lastModified();
            if (lml>=0)
                response.setDateHeader(HttpHeaders.LAST_MODIFIED,lml);

            if (count != -1)
            {
                if (count<Integer.MAX_VALUE)
                    response.setContentLength((int)count);
                else 
                    response.setHeader(HttpHeaders.CONTENT_LENGTH,TypeUtil.toString(count));
            }

            if (_acceptRanges)
                response.setHeader(HttpHeaders.ACCEPT_RANGES,"bytes");
            
            if (_cacheControl!=null)
                response.setHeader(HttpHeaders.CACHE_CONTROL,_cacheControl.toString());
        }
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.Servlet#destroy()
     */
    public void destroy()
    {
        try
        {
            if (_nioCache!=null)
                _nioCache.stop();
            if (_bioCache!=null)
                _bioCache.stop();
        }
        catch(Exception e)
        {
            Log.warn(Log.EXCEPTION,e);
        }
        finally
        {
            super.destroy();
        }
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class UnCachedContent implements HttpContent
    {
        Resource _resource;
        
        UnCachedContent(Resource resource)
        {
            _resource=resource;
        }
        
        /* ------------------------------------------------------------ */
        public Buffer getContentType()
        {
            return _mimeTypes.getMimeByExtension(_resource.toString());
        }

        /* ------------------------------------------------------------ */
        public Buffer getLastModified()
        {
            return null;
        }

        /* ------------------------------------------------------------ */
        public Buffer getBuffer()
        {
            return null;
        }

        /* ------------------------------------------------------------ */
        public long getContentLength()
        {
            return _resource.length();
        }

        /* ------------------------------------------------------------ */
        public InputStream getInputStream() throws IOException
        {
            return _resource.getInputStream();
        }

        /* ------------------------------------------------------------ */
        public Resource getResource()
        {
            return _resource;
        }

        /* ------------------------------------------------------------ */
        public void release()
        {
            _resource.release();
            _resource=null;
        }
        
    }
}
