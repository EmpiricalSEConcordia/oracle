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
package org.eclipse.jetty.server.handler;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.eclipse.jetty.util.StringUtil;


/* ------------------------------------------------------------ */
/** Handler for Error pages
 * A handler that is registered at the org.eclipse.http.ErrorHandler
 * context attributed and called by the HttpResponse.sendError method to write a
 * error page.
 * 
 * 
 */
public class ErrorHandler extends AbstractHandler
{
    boolean _showStacks=true;
    
    /* ------------------------------------------------------------ */
    /* 
     * @see org.eclipse.jetty.server.server.Handler#handle(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, int)
     */
    public void handle(String target, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        HttpConnection connection = HttpConnection.getCurrentConnection();
        connection.getRequest().setHandled(true);
        String method = request.getMethod();
        if(!method.equals(HttpMethods.GET) && !method.equals(HttpMethods.POST))
            return;
        response.setContentType(MimeTypes.TEXT_HTML_8859_1);        
        response.setHeader(HttpHeaders.CACHE_CONTROL, "must-revalidate,no-cache,no-store");
        ByteArrayISO8859Writer writer= new ByteArrayISO8859Writer(4096);
        handleErrorPage(request, writer, connection.getResponse().getStatus(), connection.getResponse().getReason());
        writer.flush();
        response.setContentLength(writer.size());
        writer.writeTo(response.getOutputStream());
        writer.destroy();
    }

    /* ------------------------------------------------------------ */
    protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message)
        throws IOException
    {
        writeErrorPage(request, writer, code, message, _showStacks);
    }
    
    /* ------------------------------------------------------------ */
    protected void writeErrorPage(HttpServletRequest request, Writer writer, int code, String message, boolean showStacks)
        throws IOException
    {
        if (message == null)
            message=HttpStatus.getCode(code).getMessage();
        else
        {
            message= StringUtil.replace(message, "&", "&amp;");
            message= StringUtil.replace(message, "<", "&lt;");
            message= StringUtil.replace(message, ">", "&gt;");
        }

        writer.write("<html>\n<head>\n");
        writeErrorPageHead(request,writer,code,message);
        writer.write("</head>\n<body>");
        writeErrorPageBody(request,writer,code,message,showStacks);
        writer.write("\n</body>\n</html>\n");
    }

    /* ------------------------------------------------------------ */
    protected void writeErrorPageHead(HttpServletRequest request, Writer writer, int code, String message)
        throws IOException
    {
        writer.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"/>\n");
        writer.write("<title>Error ");
        writer.write(Integer.toString(code));
        writer.write(' ');
        if (message!=null)
            writer.write(message);
        writer.write("</title>\n");    
    }

    /* ------------------------------------------------------------ */
    protected void writeErrorPageBody(HttpServletRequest request, Writer writer, int code, String message, boolean showStacks)
        throws IOException
    {
        String uri= request.getRequestURI();
        if (uri!=null)
        {
            uri= StringUtil.replace(uri, "&", "&amp;");
            uri= StringUtil.replace(uri, "<", "&lt;");
            uri= StringUtil.replace(uri, ">", "&gt;");
        }
        
        writeErrorPageMessage(request,writer,code,message,uri);
        if (showStacks)
            writeErrorPageStacks(request,writer);
        writer.write("<hr /><i><small>Powered by Jetty://</small></i>");
        for (int i= 0; i < 20; i++)
            writer.write("<br/>                                                \n");
    }

    /* ------------------------------------------------------------ */
    protected void writeErrorPageMessage(HttpServletRequest request, Writer writer, int code, String message,String uri)
    throws IOException
    {
        writer.write("<h2>HTTP ERROR ");
        writer.write(Integer.toString(code));
        writer.write("</h2>\n<p>Problem accessing ");
        writer.write(uri);
        writer.write(". Reason:\n<pre>    ");
        writer.write(message);
        writer.write("</pre></p>");
    }

    /* ------------------------------------------------------------ */
    protected void writeErrorPageStacks(HttpServletRequest request, Writer writer)
        throws IOException
    {
        Throwable th = (Throwable)request.getAttribute("javax.servlet.error.exception");
        while(th!=null)
        {
            writer.write("<h3>Caused by:</h3><pre>");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            th.printStackTrace(pw);
            pw.flush();
            writer.write(sw.getBuffer().toString());
            writer.write("</pre>\n");

            th =th.getCause();
        }
    }
        

    /* ------------------------------------------------------------ */
    /**
     * @return True if stack traces are shown in the error pages
     */
    public boolean isShowStacks()
    {
        return _showStacks;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param showStacks True if stack traces are shown in the error pages
     */
    public void setShowStacks(boolean showStacks)
    {
        _showStacks = showStacks;
    }

}
