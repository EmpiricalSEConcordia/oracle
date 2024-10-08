// ========================================================================
// Copyright (c) 2008-2009 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.security.authentication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.security.Constraint;
import org.eclipse.jetty.security.Authentication;
import org.eclipse.jetty.security.DefaultAuthentication;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;

/**
 * FORM Authenticator.
 * 
 * The form authenticator redirects unauthenticated requests to a log page
 * which should use a form to gather username/password from the user and send them
 * to the /j_security_check URI within the context.  FormAuthentication is intended
 * to be used together with the {@link SessionCachingAuthenticator} so that the
 * auth results may be associated with the session.
 *  
 * This authenticator implements form authentication using dispatchers unless 
 * the {@link #__FORM_DISPATCH} init parameters is set to false.
 * 
 */
public class FormAuthenticator extends LoginAuthenticator
{
    public final static String __FORM_LOGIN_PAGE="org.eclipse.jetty.security.form_login_page";
    public final static String __FORM_ERROR_PAGE="org.eclipse.jetty.security.form_error_page";
    public final static String __FORM_DISPATCH="org.eclipse.jetty.security.dispatch";
    public final static String __J_URI = "org.eclipse.jetty.util.URI";
    public final static String __J_AUTHENTICATED = "org.eclipse.jetty.server.Auth";
    public final static String __J_SECURITY_CHECK = "/j_security_check";
    public final static String __J_USERNAME = "j_username";
    public final static String __J_PASSWORD = "j_password";
    private String _formErrorPage;
    private String _formErrorPath;
    private String _formLoginPage;
    private String _formLoginPath;
    private boolean _dispatch;

    public FormAuthenticator()
    {
    }

    /* ------------------------------------------------------------ */
    public FormAuthenticator(String login,String error)
    {
        if (login!=null)
            setLoginPage(login);
        if (error!=null)
            setErrorPage(error);
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.security.authentication.LoginAuthenticator#setConfiguration(org.eclipse.jetty.security.Authenticator.Configuration)
     */
    @Override
    public void setConfiguration(Configuration configuration)
    {
        super.setConfiguration(configuration);
        String login=configuration.getInitParameter(FormAuthenticator.__FORM_LOGIN_PAGE);
        if (login!=null)
            setLoginPage(login);
        String error=configuration.getInitParameter(FormAuthenticator.__FORM_ERROR_PAGE);
        if (error!=null)
            setErrorPage(error);
        String dispatch=configuration.getInitParameter(FormAuthenticator.__FORM_DISPATCH);
        _dispatch=dispatch==null || Boolean.getBoolean(dispatch);
    }

    /* ------------------------------------------------------------ */
    public String getAuthMethod()
    {
        return Constraint.__FORM_AUTH;
    }

    /* ------------------------------------------------------------ */
    private void setLoginPage(String path)
    {
        if (!path.startsWith("/"))
        {
            Log.warn("form-login-page must start with /");
            path = "/" + path;
        }
        _formLoginPage = path;
        _formLoginPath = path;
        if (_formLoginPath.indexOf('?') > 0) 
            _formLoginPath = _formLoginPath.substring(0, _formLoginPath.indexOf('?'));
    }

    /* ------------------------------------------------------------ */
    private void setErrorPage(String path)
    {
        if (path == null || path.trim().length() == 0)
        {
            _formErrorPath = null;
            _formErrorPage = null;
        }
        else
        {
            if (!path.startsWith("/"))
            {
                Log.warn("form-error-page must start with /");
                path = "/" + path;
            }
            _formErrorPage = path;
            _formErrorPath = path;

            if (_formErrorPath.indexOf('?') > 0) 
                _formErrorPath = _formErrorPath.substring(0, _formErrorPath.indexOf('?'));
        }
    }

    /* ------------------------------------------------------------ */
    public Authentication validateRequest(ServletRequest req, ServletResponse res, boolean mandatory) throws ServerAuthException
    {
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;
        HttpSession session = request.getSession(mandatory);
        String uri = request.getPathInfo();
        // not mandatory and not authenticated
        if (session == null || isLoginOrErrorPage(uri)) 
        {
            return DefaultAuthentication.SUCCESS_UNAUTH_RESULTS;
        }
            

        try
        {
            // Handle a request for authentication.
            // TODO perhaps j_securitycheck can be uri suffix?
            if (uri.endsWith(__J_SECURITY_CHECK))
            {
                final String username = request.getParameter(__J_USERNAME);
                final char[] password = request.getParameter(__J_PASSWORD).toCharArray();
                
                UserIdentity user = _loginService.login(username,password);
                if (user!=null)
                {
                    // Redirect to original request
                    String nuri = (String) session.getAttribute(__J_URI);
                    if (nuri == null || nuri.length() == 0)
                    {
                        nuri = request.getContextPath();
                        if (nuri.length() == 0) nuri = URIUtil.SLASH;
                    }
                    // TODO shouldn't we forward to original URI instead?
                    session.removeAttribute(__J_URI); // Remove popped return URI.
                    response.setContentLength(0);   
                    response.sendRedirect(response.encodeRedirectURL(nuri));
                    return new DefaultAuthentication(Authentication.Status.SEND_SUCCESS,Constraint.__FORM_AUTH,user);
                }
                
                // not authenticated
                if (Log.isDebugEnabled()) Log.debug("Form authentication FAILED for " + StringUtil.printable(username));
                if (_formErrorPage == null)
                {
                    if (response != null) 
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                }
                else if (_dispatch)
                {
                    RequestDispatcher dispatcher = request.getRequestDispatcher(_formErrorPage);
                    response.setHeader(HttpHeaders.CACHE_CONTROL,"No-cache");
                    response.setDateHeader(HttpHeaders.EXPIRES,1);
                    dispatcher.forward(new FormRequest(request), new FormResponse(response));
                }
                else
                {
                    response.sendRedirect(URIUtil.addPaths(request.getContextPath(),_formErrorPage));
                }
                
                // TODO is this correct response if isMandatory false??? Can
                // that occur?
                return DefaultAuthentication.SEND_FAILURE_RESULTS;
            }
            // Check if the session is already authenticated.

            // Don't authenticate authform or errorpage
            if (!mandatory)
            // TODO verify this is correct action
                return DefaultAuthentication.SUCCESS_UNAUTH_RESULTS;

            // redirect to login page
            if (request.getQueryString() != null)
                uri += "?" + request.getQueryString();
            //TODO is this safe if the client is sending several requests concurrently in the same session to secured resources?
            session.setAttribute(__J_URI, request.getScheme() + "://"
                                          + request.getServerName()
                                          + ":"
                                          + request.getServerPort()
                                          + URIUtil.addPaths(request.getContextPath(), uri));
            
            if (_dispatch)
            {
                RequestDispatcher dispatcher = request.getRequestDispatcher(_formLoginPage);
                response.setHeader(HttpHeaders.CACHE_CONTROL,"No-cache");
                response.setDateHeader(HttpHeaders.EXPIRES,1);
                dispatcher.forward(new FormRequest(request), new FormResponse(response));
            }
            else
            {
                response.sendRedirect(URIUtil.addPaths(request.getContextPath(),_formLoginPage));
            }
            
            return DefaultAuthentication.SEND_CONTINUE_RESULTS;
        }
        catch (IOException e)
        {
            throw new ServerAuthException(e);
        }
        catch (ServletException e)
        {
            throw new ServerAuthException(e);
        }
    }

    /* ------------------------------------------------------------ */
    public boolean isLoginOrErrorPage(String pathInContext)
    {
        return pathInContext != null && (pathInContext.equals(_formErrorPath) || pathInContext.equals(_formLoginPath));
    }

    /* ------------------------------------------------------------ */
    public Authentication.Status secureResponse(ServletRequest req, ServletResponse res, boolean mandatory, Authentication validatedUser) throws ServerAuthException
    {
        return Authentication.Status.SUCCESS;
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    protected static class FormRequest extends HttpServletRequestWrapper
    {
        public FormRequest(HttpServletRequest request)
        {
            super(request);
        }

        @Override
        public long getDateHeader(String name)
        {
            if (name.toLowerCase().startsWith("if-"))
                return -1;
            return super.getDateHeader(name);
        }
        
        @Override
        public String getHeader(String name)
        {
            if (name.toLowerCase().startsWith("if-"))
                return null;
            return super.getHeader(name);
        }

        @Override
        public Enumeration getHeaderNames()
        {
            return Collections.enumeration(Collections.list(super.getHeaderNames()));
        }

        @Override
        public Enumeration getHeaders(String name)
        {
            if (name.toLowerCase().startsWith("if-"))
                return Collections.enumeration(Collections.EMPTY_LIST);
            return super.getHeaders(name);
        }
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    protected static class FormResponse extends HttpServletResponseWrapper
    {
        public FormResponse(HttpServletResponse response)
        {
            super(response);
        }

        @Override
        public void addDateHeader(String name, long date)
        {
            if (notIgnored(name))
                super.addDateHeader(name,date);
        }

        @Override
        public void addHeader(String name, String value)
        {
            if (notIgnored(name))
                super.addHeader(name,value);
        }

        @Override
        public void setDateHeader(String name, long date)
        {
            if (notIgnored(name))
                super.setDateHeader(name,date);
        }
        
        @Override
        public void setHeader(String name, String value)
        {
            if (notIgnored(name))
                super.setHeader(name,value);
        }
        
        private boolean notIgnored(String name)
        {
            if (HttpHeaders.CACHE_CONTROL.equalsIgnoreCase(name) ||
                HttpHeaders.PRAGMA.equalsIgnoreCase(name) ||
                HttpHeaders.ETAG.equalsIgnoreCase(name) ||
                HttpHeaders.EXPIRES.equalsIgnoreCase(name) ||
                HttpHeaders.LAST_MODIFIED.equalsIgnoreCase(name) ||
                HttpHeaders.AGE.equalsIgnoreCase(name))
                return false;
            return true;
        }
    }
}
