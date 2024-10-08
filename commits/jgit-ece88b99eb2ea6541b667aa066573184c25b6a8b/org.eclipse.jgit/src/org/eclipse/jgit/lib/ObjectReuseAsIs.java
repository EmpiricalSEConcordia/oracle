/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.lib;

import java.io.IOException;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.revwalk.RevObject;

/**
 * Extension of {@link ObjectReader} that supports reusing objects in packs.
 * <p>
 * {@code ObjectReader} implementations may also optionally implement this
 * interface to support {@link PackWriter} with a means of copying an object
 * that is already in pack encoding format directly into the output stream,
 * without incurring decompression and recompression overheads.
 */
public interface ObjectReuseAsIs {
	/**
	 * Allocate a new {@code PackWriter} state structure for an object.
	 * <p>
	 * {@link PackWriter} allocates these objects to keep track of the
	 * per-object state, and how to load the objects efficiently into the
	 * generated stream. Implementers may subclass this type with additional
	 * object state, such as to remember what file and offset contains the
	 * object's pack encoded data.
	 *
	 * @param obj
	 *            identity of the object that will be packed. The object's
	 *            parsed status is undefined here. Implementers must not rely on
	 *            the object being parsed.
	 * @return a new instance for this object.
	 */
	public ObjectToPack newObjectToPack(RevObject obj);

	/**
	 * Select the best object representation for a packer.
	 * <p>
	 * Implementations should iterate through all available representations of
	 * an object, and pass them in turn to the PackWriter though
	 * {@link PackWriter#select(ObjectToPack, StoredObjectRepresentation)} so
	 * the writer can select the most suitable representation to reuse into the
	 * output stream.
	 *
	 * @param packer
	 *            the packer that will write the object in the near future.
	 * @param otp
	 *            the object to pack.
	 * @throws MissingObjectException
	 *             there is no representation available for the object, as it is
	 *             no longer in the repository. Packing will abort.
	 * @throws IOException
	 *             the repository cannot be accessed. Packing will abort.
	 */
	public void selectObjectRepresentation(PackWriter packer, ObjectToPack otp)
			throws IOException, MissingObjectException;

	/**
	 * Output a previously selected representation.
	 * <p>
	 * {@code PackWriter} invokes this method only if a representation
	 * previously given to it by {@code selectObjectRepresentation} was chosen
	 * for reuse into the output stream. The {@code otp} argument is an instance
	 * created by this reader's own {@code newObjectToPack}, and the
	 * representation data saved within it also originated from this reader.
	 * <p>
	 * Implementors must write the object header before copying the raw data to
	 * the output stream. The typical implementation is like:
	 *
	 * <pre>
	 * MyToPack mtp = (MyToPack) otp;
	 * byte[] raw = validate(mtp); // throw SORNAE here, if at all
	 * out.writeHeader(mtp, mtp.inflatedSize);
	 * out.write(raw);
	 * </pre>
	 *
	 * @param out
	 *            stream the object should be written to.
	 * @param otp
	 *            the object's saved representation information.
	 * @throws StoredObjectRepresentationNotAvailableException
	 *             the previously selected representation is no longer
	 *             available. If thrown before {@code out.writeHeader} the pack
	 *             writer will try to find another representation, and write
	 *             that one instead. If throw after {@code out.writeHeader},
	 *             packing will abort.
	 * @throws IOException
	 *             the stream's write method threw an exception. Packing will
	 *             abort.
	 */
	public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp)
			throws IOException, StoredObjectRepresentationNotAvailableException;
}
