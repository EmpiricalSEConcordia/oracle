/*
 * Copyright (C) 2009-2010, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.http.server;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static javax.servlet.http.HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_HANDLER;
import static org.eclipse.jgit.http.server.ServletUtils.getInputStream;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;

import java.io.IOException;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.UploadPackMayNotContinueException;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

/** Server side implementation of smart fetch over HTTP. */
class UploadPackServlet extends HttpServlet {
	private static final String REQ_TYPE = "application/x-git-upload-pack-request";

	private static final String RSP_TYPE = "application/x-git-upload-pack-result";

	private static final long serialVersionUID = 1L;

	static class InfoRefs extends SmartServiceInfoRefs {
		private final UploadPackFactory<HttpServletRequest> uploadPackFactory;

		InfoRefs(UploadPackFactory<HttpServletRequest> uploadPackFactory,
				List<Filter> filters) {
			super("git-upload-pack", filters);
			this.uploadPackFactory = uploadPackFactory;
		}

		@Override
		protected void begin(HttpServletRequest req, Repository db)
				throws IOException, ServiceNotEnabledException,
				ServiceNotAuthorizedException {
			UploadPack up = uploadPackFactory.create(req, db);
			req.setAttribute(ATTRIBUTE_HANDLER, up);
		}

		@Override
		protected void advertise(HttpServletRequest req,
				PacketLineOutRefAdvertiser pck) throws IOException,
				ServiceNotEnabledException, ServiceNotAuthorizedException {
			UploadPack up = (UploadPack) req.getAttribute(ATTRIBUTE_HANDLER);
			try {
				up.sendAdvertisedRefs(pck);
			} finally {
				up.getRevWalk().release();
			}
		}
	}

	static class Factory implements Filter {
		private final UploadPackFactory<HttpServletRequest> uploadPackFactory;

		Factory(UploadPackFactory<HttpServletRequest> uploadPackFactory) {
			this.uploadPackFactory = uploadPackFactory;
		}

		public void doFilter(ServletRequest request, ServletResponse response,
				FilterChain chain) throws IOException, ServletException {
			HttpServletRequest req = (HttpServletRequest) request;
			HttpServletResponse rsp = (HttpServletResponse) response;
			UploadPack rp;
			try {
				rp = uploadPackFactory.create(req, getRepository(req));
			} catch (ServiceNotAuthorizedException e) {
				rsp.sendError(SC_UNAUTHORIZED);
				return;

			} catch (ServiceNotEnabledException e) {
				rsp.sendError(SC_FORBIDDEN);
				return;
			}

			try {
				req.setAttribute(ATTRIBUTE_HANDLER, rp);
				chain.doFilter(req, rsp);
			} finally {
				req.removeAttribute(ATTRIBUTE_HANDLER);
			}
		}

		public void init(FilterConfig filterConfig) throws ServletException {
			// Nothing.
		}

		public void destroy() {
			// Nothing.
		}
	}

	@Override
	public void doPost(final HttpServletRequest req,
			final HttpServletResponse rsp) throws IOException {
		if (!REQ_TYPE.equals(req.getContentType())) {
			rsp.sendError(SC_UNSUPPORTED_MEDIA_TYPE);
			return;
		}

		UploadPack up = (UploadPack) req.getAttribute(ATTRIBUTE_HANDLER);
		try {
			up.setBiDirectionalPipe(false);
			rsp.setContentType(RSP_TYPE);

			final SmartOutputStream out = new SmartOutputStream(req, rsp) {
				@Override
				public void flush() throws IOException {
					doFlush();
				}
			};
			up.upload(getInputStream(req), out, null);
			out.close();

		} catch (UploadPackMayNotContinueException e) {
			if (!e.isOutput())
				rsp.sendError(SC_SERVICE_UNAVAILABLE);
			return;

		} catch (IOException e) {
			getServletContext().log(HttpServerText.get().internalErrorDuringUploadPack, e);
			rsp.reset();
			rsp.sendError(SC_INTERNAL_SERVER_ERROR);
			return;
		}
	}
}
