/*
 * Copyright (C) 2011, Ketan Padegaonkar <KetanPadegaonkar@gmail.com>
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
package org.eclipse.jgit.ant.tasks;

import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.URIish;

/**
 * Clone a repository into a new directory.
 * 
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-clone.html"
 *      >git-clone(1)</a>
 */
public class GitCloneTask extends Task {

	private String uri;
	private File destination;
	private boolean bare;
	private String branch = Constants.HEAD;

	/**
	 * @param uri
	 *            the uri to clone from
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}

	/**
	 * The optional directory associated with the clone operation. If the
	 * directory isn't set, a name associated with the source uri will be used.
	 * 
	 * @see URIish#getHumanishName()
	 * 
	 * @param destination
	 *            the directory to clone to
	 */
	public void setDest(File destination) {
		this.destination = destination;
	}

	/**
	 * @param bare
	 *            whether the cloned repository is bare or not
	 */
	public void setBare(boolean bare) {
		this.bare = bare;
	}

	/**
	 * @param branch
	 *            the initial branch to check out when cloning the repository
	 */
	public void setBranch(String branch) {
		this.branch = branch;
	}

	@Override
	public void execute() throws BuildException {
		log("Cloning repository " + uri);
		
		CloneCommand clone = Git.cloneRepository();
		try {
			clone.setURI(uri).setDirectory(destination).setBranch(branch).setBare(bare);
			clone.call();
		} catch (Exception e) {
			log("Could not clone repository: " + e, e, Project.MSG_ERR);
			throw new BuildException("Could not clone repository: " + e.getMessage(), e);
		}
	}
}
