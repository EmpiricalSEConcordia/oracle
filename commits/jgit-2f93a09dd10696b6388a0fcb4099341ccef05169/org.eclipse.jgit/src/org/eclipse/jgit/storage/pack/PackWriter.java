/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

package org.eclipse.jgit.storage.pack;

import static org.eclipse.jgit.storage.pack.StoredObjectRepresentation.PACK_DELTA;
import static org.eclipse.jgit.storage.pack.StoredObjectRepresentation.PACK_WHOLE;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.storage.file.PackIndexWriter;

/**
 * <p>
 * PackWriter class is responsible for generating pack files from specified set
 * of objects from repository. This implementation produce pack files in format
 * version 2.
 * </p>
 * <p>
 * Source of objects may be specified in two ways:
 * <ul>
 * <li>(usually) by providing sets of interesting and uninteresting objects in
 * repository - all interesting objects and their ancestors except uninteresting
 * objects and their ancestors will be included in pack, or</li>
 * <li>by providing iterator of {@link RevObject} specifying exact list and
 * order of objects in pack</li>
 * </ul>
 * Typical usage consists of creating instance intended for some pack,
 * configuring options, preparing the list of objects by calling
 * {@link #preparePack(Iterator)} or
 * {@link #preparePack(ProgressMonitor, Collection, Collection)}, and finally
 * producing the stream with {@link #writePack(ProgressMonitor, ProgressMonitor, OutputStream)}.
 * </p>
 * <p>
 * Class provide set of configurable options and {@link ProgressMonitor}
 * support, as operations may take a long time for big repositories. Deltas
 * searching algorithm is <b>NOT IMPLEMENTED</b> yet - this implementation
 * relies only on deltas and objects reuse.
 * </p>
 * <p>
 * This class is not thread safe, it is intended to be used in one thread, with
 * one instance per created pack. Subsequent calls to writePack result in
 * undefined behavior.
 * </p>
 */
public class PackWriter {
	/**
	 * Title of {@link ProgressMonitor} task used during counting objects to
	 * pack.
	 *
	 * @see #preparePack(ProgressMonitor, Collection, Collection)
	 */
	public static final String COUNTING_OBJECTS_PROGRESS = JGitText.get().countingObjects;

	/**
	 * Title of {@link ProgressMonitor} task used during searching for objects
	 * reuse or delta reuse.
	 *
	 * @see #writePack(ProgressMonitor, ProgressMonitor, OutputStream)
	 */
	public static final String SEARCHING_REUSE_PROGRESS = JGitText.get().compressingObjects;

	/**
	 * Title of {@link ProgressMonitor} task used during writing out pack
	 * (objects)
	 *
	 * @see #writePack(ProgressMonitor, ProgressMonitor, OutputStream)
	 */
	public static final String WRITING_OBJECTS_PROGRESS = JGitText.get().writingObjects;

	/**
	 * Default value of deltas reuse option.
	 *
	 * @see #setReuseDeltas(boolean)
	 */
	public static final boolean DEFAULT_REUSE_DELTAS = true;

	/**
	 * Default value of objects reuse option.
	 *
	 * @see #setReuseObjects(boolean)
	 */
	public static final boolean DEFAULT_REUSE_OBJECTS = true;

	/**
	 * Default value of delta base as offset option.
	 *
	 * @see #setDeltaBaseAsOffset(boolean)
	 */
	public static final boolean DEFAULT_DELTA_BASE_AS_OFFSET = false;

	/**
	 * Default value of maximum delta chain depth.
	 *
	 * @see #setMaxDeltaDepth(int)
	 */
	public static final int DEFAULT_MAX_DELTA_DEPTH = 50;

	private static final int PACK_VERSION_GENERATED = 2;

	@SuppressWarnings("unchecked")
	private final List<ObjectToPack> objectsLists[] = new List[Constants.OBJ_TAG + 1];
	{
		objectsLists[0] = Collections.<ObjectToPack> emptyList();
		objectsLists[Constants.OBJ_COMMIT] = new ArrayList<ObjectToPack>();
		objectsLists[Constants.OBJ_TREE] = new ArrayList<ObjectToPack>();
		objectsLists[Constants.OBJ_BLOB] = new ArrayList<ObjectToPack>();
		objectsLists[Constants.OBJ_TAG] = new ArrayList<ObjectToPack>();
	}

	private final ObjectIdSubclassMap<ObjectToPack> objectsMap = new ObjectIdSubclassMap<ObjectToPack>();

	// edge objects for thin packs
	private final ObjectIdSubclassMap<ObjectToPack> edgeObjects = new ObjectIdSubclassMap<ObjectToPack>();

	private int compressionLevel;

	private Deflater myDeflater;

	private final ObjectReader reader;

	/** {@link #reader} recast to the reuse interface, if it supports it. */
	private final ObjectReuseAsIs reuseSupport;

	private List<ObjectToPack> sortedByName;

	private byte packcsum[];

	private boolean reuseDeltas = DEFAULT_REUSE_DELTAS;

	private boolean reuseObjects = DEFAULT_REUSE_OBJECTS;

	private boolean deltaBaseAsOffset = DEFAULT_DELTA_BASE_AS_OFFSET;

	private int maxDeltaDepth = DEFAULT_MAX_DELTA_DEPTH;

	private int outputVersion;

	private boolean thin;

	private boolean ignoreMissingUninteresting = true;

	/**
	 * Create writer for specified repository.
	 * <p>
	 * Objects for packing are specified in {@link #preparePack(Iterator)} or
	 * {@link #preparePack(ProgressMonitor, Collection, Collection)}.
	 *
	 * @param repo
	 *            repository where objects are stored.
	 */
	public PackWriter(final Repository repo) {
		this(repo, repo.newObjectReader());
	}

	/**
	 * Create a writer to load objects from the specified reader.
	 * <p>
	 * Objects for packing are specified in {@link #preparePack(Iterator)} or
	 * {@link #preparePack(ProgressMonitor, Collection, Collection)}.
	 *
	 * @param reader
	 *            reader to read from the repository with.
	 */
	public PackWriter(final ObjectReader reader) {
		this(null, reader);
	}

	/**
	 * Create writer for specified repository.
	 * <p>
	 * Objects for packing are specified in {@link #preparePack(Iterator)} or
	 * {@link #preparePack(ProgressMonitor, Collection, Collection)}.
	 *
	 * @param repo
	 *            repository where objects are stored.
	 * @param reader
	 *            reader to read from the repository with.
	 */
	public PackWriter(final Repository repo, final ObjectReader reader) {
		this.reader = reader;
		if (reader instanceof ObjectReuseAsIs)
			reuseSupport = ((ObjectReuseAsIs) reader);
		else
			reuseSupport = null;

		final CoreConfig coreConfig = configOf(repo).get(CoreConfig.KEY);
		compressionLevel = coreConfig.getCompression();
		outputVersion = coreConfig.getPackIndexVersion();
	}

	private static Config configOf(final Repository repo) {
		if (repo == null)
			return new Config();
		return repo.getConfig();
	}

	/**
	 * Check whether object is configured to reuse deltas existing in
	 * repository.
	 * <p>
	 * Default setting: {@value #DEFAULT_REUSE_DELTAS}
	 * </p>
	 *
	 * @return true if object is configured to reuse deltas; false otherwise.
	 */
	public boolean isReuseDeltas() {
		return reuseDeltas;
	}

	/**
	 * Set reuse deltas configuration option for this writer. When enabled,
	 * writer will search for delta representation of object in repository and
	 * use it if possible. Normally, only deltas with base to another object
	 * existing in set of objects to pack will be used. Exception is however
	 * thin-pack (see
	 * {@link #preparePack(ProgressMonitor, Collection, Collection)} and
	 * {@link #preparePack(Iterator)}) where base object must exist on other
	 * side machine.
	 * <p>
	 * When raw delta data is directly copied from a pack file, checksum is
	 * computed to verify data.
	 * </p>
	 * <p>
	 * Default setting: {@value #DEFAULT_REUSE_DELTAS}
	 * </p>
	 *
	 * @param reuseDeltas
	 *            boolean indicating whether or not try to reuse deltas.
	 */
	public void setReuseDeltas(boolean reuseDeltas) {
		this.reuseDeltas = reuseDeltas;
	}

	/**
	 * Checks whether object is configured to reuse existing objects
	 * representation in repository.
	 * <p>
	 * Default setting: {@value #DEFAULT_REUSE_OBJECTS}
	 * </p>
	 *
	 * @return true if writer is configured to reuse objects representation from
	 *         pack; false otherwise.
	 */
	public boolean isReuseObjects() {
		return reuseObjects;
	}

	/**
	 * Set reuse objects configuration option for this writer. If enabled,
	 * writer searches for representation in a pack file. If possible,
	 * compressed data is directly copied from such a pack file. Data checksum
	 * is verified.
	 * <p>
	 * Default setting: {@value #DEFAULT_REUSE_OBJECTS}
	 * </p>
	 *
	 * @param reuseObjects
	 *            boolean indicating whether or not writer should reuse existing
	 *            objects representation.
	 */
	public void setReuseObjects(boolean reuseObjects) {
		this.reuseObjects = reuseObjects;
	}

	/**
	 * Check whether writer can store delta base as an offset (new style
	 * reducing pack size) or should store it as an object id (legacy style,
	 * compatible with old readers).
	 * <p>
	 * Default setting: {@value #DEFAULT_DELTA_BASE_AS_OFFSET}
	 * </p>
	 *
	 * @return true if delta base is stored as an offset; false if it is stored
	 *         as an object id.
	 */
	public boolean isDeltaBaseAsOffset() {
		return deltaBaseAsOffset;
	}

	/**
	 * Set writer delta base format. Delta base can be written as an offset in a
	 * pack file (new approach reducing file size) or as an object id (legacy
	 * approach, compatible with old readers).
	 * <p>
	 * Default setting: {@value #DEFAULT_DELTA_BASE_AS_OFFSET}
	 * </p>
	 *
	 * @param deltaBaseAsOffset
	 *            boolean indicating whether delta base can be stored as an
	 *            offset.
	 */
	public void setDeltaBaseAsOffset(boolean deltaBaseAsOffset) {
		this.deltaBaseAsOffset = deltaBaseAsOffset;
	}

	/**
	 * Get maximum depth of delta chain set up for this writer. Generated chains
	 * are not longer than this value.
	 * <p>
	 * Default setting: {@value #DEFAULT_MAX_DELTA_DEPTH}
	 * </p>
	 *
	 * @return maximum delta chain depth.
	 */
	public int getMaxDeltaDepth() {
		return maxDeltaDepth;
	}

	/**
	 * Set up maximum depth of delta chain for this writer. Generated chains are
	 * not longer than this value. Too low value causes low compression level,
	 * while too big makes unpacking (reading) longer.
	 * <p>
	 * Default setting: {@value #DEFAULT_MAX_DELTA_DEPTH}
	 * </p>
	 *
	 * @param maxDeltaDepth
	 *            maximum delta chain depth.
	 */
	public void setMaxDeltaDepth(int maxDeltaDepth) {
		this.maxDeltaDepth = maxDeltaDepth;
	}

	/** @return true if this writer is producing a thin pack. */
	public boolean isThin() {
		return thin;
	}

	/**
	 * @param packthin
	 *            a boolean indicating whether writer may pack objects with
	 *            delta base object not within set of objects to pack, but
	 *            belonging to party repository (uninteresting/boundary) as
	 *            determined by set; this kind of pack is used only for
	 *            transport; true - to produce thin pack, false - otherwise.
	 */
	public void setThin(final boolean packthin) {
		thin = packthin;
	}

	/**
	 * @return true to ignore objects that are uninteresting and also not found
	 *         on local disk; false to throw a {@link MissingObjectException}
	 *         out of {@link #preparePack(ProgressMonitor, Collection, Collection)} if an
	 *         uninteresting object is not in the source repository. By default,
	 *         true, permitting gracefully ignoring of uninteresting objects.
	 */
	public boolean isIgnoreMissingUninteresting() {
		return ignoreMissingUninteresting;
	}

	/**
	 * @param ignore
	 *            true if writer should ignore non existing uninteresting
	 *            objects during construction set of objects to pack; false
	 *            otherwise - non existing uninteresting objects may cause
	 *            {@link MissingObjectException}
	 */
	public void setIgnoreMissingUninteresting(final boolean ignore) {
		ignoreMissingUninteresting = ignore;
	}

	/**
	 * Set the pack index file format version this instance will create.
	 *
	 * @param version
	 *            the version to write. The special version 0 designates the
	 *            oldest (most compatible) format available for the objects.
	 * @see PackIndexWriter
	 */
	public void setIndexVersion(final int version) {
		outputVersion = version;
	}

	/**
	 * Returns objects number in a pack file that was created by this writer.
	 *
	 * @return number of objects in pack.
	 */
	public int getObjectsNumber() {
		return objectsMap.size();
	}

	/**
	 * Prepare the list of objects to be written to the pack stream.
	 * <p>
	 * Iterator <b>exactly</b> determines which objects are included in a pack
	 * and order they appear in pack (except that objects order by type is not
	 * needed at input). This order should conform general rules of ordering
	 * objects in git - by recency and path (type and delta-base first is
	 * internally secured) and responsibility for guaranteeing this order is on
	 * a caller side. Iterator must return each id of object to write exactly
	 * once.
	 * </p>
	 * <p>
	 * When iterator returns object that has {@link RevFlag#UNINTERESTING} flag,
	 * this object won't be included in an output pack. Instead, it is recorded
	 * as edge-object (known to remote repository) for thin-pack. In such a case
	 * writer may pack objects with delta base object not within set of objects
	 * to pack, but belonging to party repository - those marked with
	 * {@link RevFlag#UNINTERESTING} flag. This type of pack is used only for
	 * transport.
	 * </p>
	 *
	 * @param objectsSource
	 *            iterator of object to store in a pack; order of objects within
	 *            each type is important, ordering by type is not needed;
	 *            allowed types for objects are {@link Constants#OBJ_COMMIT},
	 *            {@link Constants#OBJ_TREE}, {@link Constants#OBJ_BLOB} and
	 *            {@link Constants#OBJ_TAG}; objects returned by iterator may
	 *            be later reused by caller as object id and type are internally
	 *            copied in each iteration; if object returned by iterator has
	 *            {@link RevFlag#UNINTERESTING} flag set, it won't be included
	 *            in a pack, but is considered as edge-object for thin-pack.
	 * @throws IOException
	 *             when some I/O problem occur during reading objects.
	 */
	public void preparePack(final Iterator<RevObject> objectsSource)
			throws IOException {
		while (objectsSource.hasNext()) {
			addObject(objectsSource.next());
		}
	}

	/**
	 * Prepare the list of objects to be written to the pack stream.
	 * <p>
	 * Basing on these 2 sets, another set of objects to put in a pack file is
	 * created: this set consists of all objects reachable (ancestors) from
	 * interesting objects, except uninteresting objects and their ancestors.
	 * This method uses class {@link ObjectWalk} extensively to find out that
	 * appropriate set of output objects and their optimal order in output pack.
	 * Order is consistent with general git in-pack rules: sort by object type,
	 * recency, path and delta-base first.
	 * </p>
	 *
	 * @param countingMonitor
	 *            progress during object enumeration.
	 * @param interestingObjects
	 *            collection of objects to be marked as interesting (start
	 *            points of graph traversal).
	 * @param uninterestingObjects
	 *            collection of objects to be marked as uninteresting (end
	 *            points of graph traversal).
	 * @throws IOException
	 *             when some I/O problem occur during reading objects.
	 */
	public void preparePack(ProgressMonitor countingMonitor,
			final Collection<? extends ObjectId> interestingObjects,
			final Collection<? extends ObjectId> uninterestingObjects)
			throws IOException {
		if (countingMonitor == null)
			countingMonitor = NullProgressMonitor.INSTANCE;
		ObjectWalk walker = setUpWalker(interestingObjects,
				uninterestingObjects);
		findObjectsToPack(countingMonitor, walker);
	}

	/**
	 * Determine if the pack file will contain the requested object.
	 *
	 * @param id
	 *            the object to test the existence of.
	 * @return true if the object will appear in the output pack file.
	 */
	public boolean willInclude(final AnyObjectId id) {
		return objectsMap.get(id) != null;
	}

	/**
	 * Computes SHA-1 of lexicographically sorted objects ids written in this
	 * pack, as used to name a pack file in repository.
	 *
	 * @return ObjectId representing SHA-1 name of a pack that was created.
	 */
	public ObjectId computeName() {
		final byte[] buf = new byte[Constants.OBJECT_ID_LENGTH];
		final MessageDigest md = Constants.newMessageDigest();
		for (ObjectToPack otp : sortByName()) {
			otp.copyRawTo(buf, 0);
			md.update(buf, 0, Constants.OBJECT_ID_LENGTH);
		}
		return ObjectId.fromRaw(md.digest());
	}

	/**
	 * Create an index file to match the pack file just written.
	 * <p>
	 * This method can only be invoked after {@link #preparePack(Iterator)} or
	 * {@link #preparePack(ProgressMonitor, Collection, Collection)} has been
	 * invoked and completed successfully. Writing a corresponding index is an
	 * optional feature that not all pack users may require.
	 *
	 * @param indexStream
	 *            output for the index data. Caller is responsible for closing
	 *            this stream.
	 * @throws IOException
	 *             the index data could not be written to the supplied stream.
	 */
	public void writeIndex(final OutputStream indexStream) throws IOException {
		final List<ObjectToPack> list = sortByName();
		final PackIndexWriter iw;
		if (outputVersion <= 0)
			iw = PackIndexWriter.createOldestPossible(indexStream, list);
		else
			iw = PackIndexWriter.createVersion(indexStream, outputVersion);
		iw.write(list, packcsum);
	}

	private List<ObjectToPack> sortByName() {
		if (sortedByName == null) {
			sortedByName = new ArrayList<ObjectToPack>(objectsMap.size());
			for (List<ObjectToPack> list : objectsLists) {
				for (ObjectToPack otp : list)
					sortedByName.add(otp);
			}
			Collections.sort(sortedByName);
		}
		return sortedByName;
	}

	/**
	 * Write the prepared pack to the supplied stream.
	 * <p>
	 * At first, this method collects and sorts objects to pack, then deltas
	 * search is performed if set up accordingly, finally pack stream is
	 * written. {@link ProgressMonitor} tasks {@value #SEARCHING_REUSE_PROGRESS}
	 * (only if reuseDeltas or reuseObjects is enabled) and
	 * {@value #WRITING_OBJECTS_PROGRESS} are updated during packing.
	 * </p>
	 * <p>
	 * All reused objects data checksum (Adler32/CRC32) is computed and
	 * validated against existing checksum.
	 * </p>
	 *
	 * @param compressMonitor
	 *            progress monitor to report object compression work.
	 * @param writeMonitor
	 *            progress monitor to report the number of objects written.
	 * @param packStream
	 *            output stream of pack data. The stream should be buffered by
	 *            the caller. The caller is responsible for closing the stream.
	 * @throws IOException
	 *             an error occurred reading a local object's data to include in
	 *             the pack, or writing compressed object data to the output
	 *             stream.
	 */
	public void writePack(ProgressMonitor compressMonitor,
			ProgressMonitor writeMonitor, OutputStream packStream)
			throws IOException {
		if (compressMonitor == null)
			compressMonitor = NullProgressMonitor.INSTANCE;
		if (writeMonitor == null)
			writeMonitor = NullProgressMonitor.INSTANCE;

		if ((reuseDeltas || reuseObjects) && reuseSupport != null)
			searchForReuse(compressMonitor);

		final PackOutputStream out = new PackOutputStream(writeMonitor,
				packStream, isDeltaBaseAsOffset());

		writeMonitor.beginTask(WRITING_OBJECTS_PROGRESS, getObjectsNumber());
		out.writeFileHeader(PACK_VERSION_GENERATED, getObjectsNumber());
		writeObjects(writeMonitor, out);
		writeChecksum(out);

		reader.release();
		writeMonitor.endTask();
	}

	/** Release all resources used by this writer. */
	public void release() {
		reader.release();
		if (myDeflater != null) {
			myDeflater.end();
			myDeflater = null;
		}
	}

	private void searchForReuse(ProgressMonitor compressMonitor)
			throws IOException {
		compressMonitor.beginTask(SEARCHING_REUSE_PROGRESS, getObjectsNumber());
		for (List<ObjectToPack> list : objectsLists) {
			for (ObjectToPack otp : list) {
				if (compressMonitor.isCancelled())
					throw new IOException(
							JGitText.get().packingCancelledDuringObjectsWriting);
				reuseSupport.selectObjectRepresentation(this, otp);
				compressMonitor.update(1);
			}
		}
		compressMonitor.endTask();
	}

	private void writeObjects(ProgressMonitor writeMonitor, PackOutputStream out)
			throws IOException {
		for (List<ObjectToPack> list : objectsLists) {
			for (ObjectToPack otp : list) {
				if (writeMonitor.isCancelled())
					throw new IOException(
							JGitText.get().packingCancelledDuringObjectsWriting);
				if (!otp.isWritten())
					writeObject(out, otp);
			}
		}
	}

	private void writeObject(PackOutputStream out, final ObjectToPack otp)
			throws IOException {
		if (otp.isWritten())
			return; // We shouldn't be here.

		otp.markWantWrite();
		if (otp.isDeltaRepresentation())
			writeBaseFirst(out, otp);

		out.resetCRC32();
		otp.setOffset(out.length());

		while (otp.isReuseAsIs()) {
			try {
				reuseSupport.copyObjectAsIs(out, otp);
				out.endObject();
				otp.setCRC(out.getCRC32());
				return;
			} catch (StoredObjectRepresentationNotAvailableException gone) {
				if (otp.getOffset() == out.length()) {
					redoSearchForReuse(otp);
					continue;
				} else {
					// Object writing already started, we cannot recover.
					//
					CorruptObjectException coe;
					coe = new CorruptObjectException(otp, "");
					coe.initCause(gone);
					throw coe;
				}
			}
		}

		// If we reached here, reuse wasn't possible.
		//
		writeWholeObjectDeflate(out, otp);
		out.endObject();
		otp.setCRC(out.getCRC32());
	}

	private void writeBaseFirst(PackOutputStream out, final ObjectToPack otp)
			throws IOException {
		ObjectToPack baseInPack = otp.getDeltaBase();
		if (baseInPack != null) {
			if (!baseInPack.isWritten()) {
				if (baseInPack.wantWrite()) {
					// There is a cycle. Our caller is trying to write the
					// object we want as a base, and called us. Turn off
					// delta reuse so we can find another form.
					//
					reuseDeltas = false;
					redoSearchForReuse(otp);
					reuseDeltas = true;
				} else {
					writeObject(out, baseInPack);
				}
			}
		} else if (!thin) {
			// This should never occur, the base isn't in the pack and
			// the pack isn't allowed to reference base outside objects.
			// Write the object as a whole form, even if that is slow.
			//
			otp.clearDeltaBase();
			otp.clearReuseAsIs();
		}
	}

	private void redoSearchForReuse(final ObjectToPack otp) throws IOException,
			MissingObjectException {
		otp.clearDeltaBase();
		otp.clearReuseAsIs();
		reuseSupport.selectObjectRepresentation(this, otp);
	}

	private void writeWholeObjectDeflate(PackOutputStream out,
			final ObjectToPack otp) throws IOException {
		final Deflater deflater = deflater();
		final ObjectLoader ldr = reader.open(otp, otp.getType());

		out.writeHeader(otp, ldr.getSize());

		deflater.reset();
		DeflaterOutputStream dst = new DeflaterOutputStream(out, deflater);
		ldr.copyTo(dst);
		dst.finish();
	}

	private Deflater deflater() {
		if (myDeflater == null)
			myDeflater = new Deflater(compressionLevel);
		return myDeflater;
	}

	private void writeChecksum(PackOutputStream out) throws IOException {
		packcsum = out.getDigest();
		out.write(packcsum);
	}

	private ObjectWalk setUpWalker(
			final Collection<? extends ObjectId> interestingObjects,
			final Collection<? extends ObjectId> uninterestingObjects)
			throws MissingObjectException, IOException,
			IncorrectObjectTypeException {
		final ObjectWalk walker = new ObjectWalk(reader);
		walker.setRetainBody(false);
		walker.sort(RevSort.COMMIT_TIME_DESC);
		if (thin)
			walker.sort(RevSort.BOUNDARY, true);

		for (ObjectId id : interestingObjects) {
			RevObject o = walker.parseAny(id);
			walker.markStart(o);
		}
		if (uninterestingObjects != null) {
			for (ObjectId id : uninterestingObjects) {
				final RevObject o;
				try {
					o = walker.parseAny(id);
				} catch (MissingObjectException x) {
					if (ignoreMissingUninteresting)
						continue;
					throw x;
				}
				walker.markUninteresting(o);
			}
		}
		return walker;
	}

	private void findObjectsToPack(final ProgressMonitor countingMonitor,
			final ObjectWalk walker) throws MissingObjectException,
			IncorrectObjectTypeException,			IOException {
		countingMonitor.beginTask(COUNTING_OBJECTS_PROGRESS,
				ProgressMonitor.UNKNOWN);
		RevObject o;

		while ((o = walker.next()) != null) {
			addObject(o, 0);
			countingMonitor.update(1);
		}
		while ((o = walker.nextObject()) != null) {
			addObject(o, walker.getPathHashCode());
			countingMonitor.update(1);
		}
		countingMonitor.endTask();
	}

	/**
	 * Include one object to the output file.
	 * <p>
	 * Objects are written in the order they are added. If the same object is
	 * added twice, it may be written twice, creating a larger than necessary
	 * file.
	 *
	 * @param object
	 *            the object to add.
	 * @throws IncorrectObjectTypeException
	 *             the object is an unsupported type.
	 */
	public void addObject(final RevObject object)
			throws IncorrectObjectTypeException {
		addObject(object, 0);
	}

	private void addObject(final RevObject object, final int pathHashCode)
			throws IncorrectObjectTypeException {
		if (object.has(RevFlag.UNINTERESTING)) {
			switch (object.getType()) {
			case Constants.OBJ_TREE:
			case Constants.OBJ_BLOB:
				ObjectToPack otp = new ObjectToPack(object);
				otp.setPathHash(pathHashCode);
				edgeObjects.add(otp);
				thin = true;
				break;
			}
			return;
		}

		final ObjectToPack otp;
		if (reuseSupport != null)
			otp = reuseSupport.newObjectToPack(object);
		else
			otp = new ObjectToPack(object);
		otp.setPathHash(pathHashCode);

		try {
			objectsLists[object.getType()].add(otp);
		} catch (ArrayIndexOutOfBoundsException x) {
			throw new IncorrectObjectTypeException(object,
					JGitText.get().incorrectObjectType_COMMITnorTREEnorBLOBnorTAG);
		} catch (UnsupportedOperationException x) {
			// index pointing to "dummy" empty list
			throw new IncorrectObjectTypeException(object,
					JGitText.get().incorrectObjectType_COMMITnorTREEnorBLOBnorTAG);
		}
		objectsMap.add(otp);
	}

	/**
	 * Select an object representation for this writer.
	 * <p>
	 * An {@link ObjectReader} implementation should invoke this method once for
	 * each representation available for an object, to allow the writer to find
	 * the most suitable one for the output.
	 *
	 * @param otp
	 *            the object being packed.
	 * @param next
	 *            the next available representation from the repository.
	 */
	public void select(ObjectToPack otp, StoredObjectRepresentation next) {
		int nFmt = next.getFormat();
		int nWeight;
		if (otp.isReuseAsIs()) {
			// We've already chosen to reuse a packed form, if next
			// cannot beat that break out early.
			//
			if (PACK_WHOLE < nFmt)
				return; // next isn't packed
			else if (PACK_DELTA < nFmt && otp.isDeltaRepresentation())
				return; // next isn't a delta, but we are

			nWeight = next.getWeight();
			if (otp.getWeight() <= nWeight)
				return; // next would be bigger
		} else
			nWeight = next.getWeight();

		if (nFmt == PACK_DELTA && reuseDeltas) {
			ObjectId baseId = next.getDeltaBase();
			ObjectToPack ptr = objectsMap.get(baseId);
			if (ptr != null) {
				otp.setDeltaBase(ptr);
				otp.setReuseAsIs();
				otp.setWeight(nWeight);
			} else if (thin && edgeObjects.contains(baseId)) {
				otp.setDeltaBase(baseId);
				otp.setReuseAsIs();
				otp.setWeight(nWeight);
			} else {
				otp.clearDeltaBase();
				otp.clearReuseAsIs();
			}
		} else if (nFmt == PACK_WHOLE && reuseObjects) {
			otp.clearDeltaBase();
			otp.setReuseAsIs();
			otp.setWeight(nWeight);
		} else {
			otp.clearDeltaBase();
			otp.clearReuseAsIs();
		}

		otp.select(next);
	}
}
