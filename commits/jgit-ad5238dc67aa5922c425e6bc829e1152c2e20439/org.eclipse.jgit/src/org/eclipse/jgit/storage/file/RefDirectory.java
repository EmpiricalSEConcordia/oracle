/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.storage.file;

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.LOGS;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;
import static org.eclipse.jgit.lib.Constants.PACKED_REFS;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.lib.Ref.Storage.LOOSE;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.ObjectWritingException;
import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefWriter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

/**
 * Traditional file system based {@link RefDatabase}.
 * <p>
 * This is the classical reference database representation for a Git repository.
 * References are stored in two formats: loose, and packed.
 * <p>
 * Loose references are stored as individual files within the {@code refs/}
 * directory. The file name matches the reference name and the file contents is
 * the current {@link ObjectId} in string form.
 * <p>
 * Packed references are stored in a single text file named {@code packed-refs}.
 * In the packed format, each reference is stored on its own line. This file
 * reduces the number of files needed for large reference spaces, reducing the
 * overall size of a Git repository on disk.
 */
public class RefDirectory extends RefDatabase {
	/** Magic string denoting the start of a symbolic reference file. */
	public static final String SYMREF = "ref: "; //$NON-NLS-1$

	/** Magic string denoting the header of a packed-refs file. */
	public static final String PACKED_REFS_HEADER = "# pack-refs with:"; //$NON-NLS-1$

	/** If in the header, denotes the file has peeled data. */
	public static final String PACKED_REFS_PEELED = " peeled"; //$NON-NLS-1$

	private final FileRepository parent;

	private final File gitDir;

	private final File refsDir;

	private final File logsDir;

	private final File logsRefsDir;

	private final File packedRefsFile;

	/**
	 * Immutable sorted list of loose references.
	 * <p>
	 * Symbolic references in this collection are stored unresolved, that is
	 * their target appears to be a new reference with no ObjectId. These are
	 * converted into resolved references during a get operation, ensuring the
	 * live value is always returned.
	 */
	private final AtomicReference<RefList<LooseRef>> looseRefs = new AtomicReference<RefList<LooseRef>>();

	/** Immutable sorted list of packed references. */
	private final AtomicReference<PackedRefList> packedRefs = new AtomicReference<PackedRefList>();

	/**
	 * Number of modifications made to this database.
	 * <p>
	 * This counter is incremented when a change is made, or detected from the
	 * filesystem during a read operation.
	 */
	private final AtomicInteger modCnt = new AtomicInteger();

	/**
	 * Last {@link #modCnt} that we sent to listeners.
	 * <p>
	 * This value is compared to {@link #modCnt}, and a notification is sent to
	 * the listeners only when it differs.
	 */
	private final AtomicInteger lastNotifiedModCnt = new AtomicInteger();

	RefDirectory(final FileRepository db) {
		final FS fs = db.getFS();
		parent = db;
		gitDir = db.getDirectory();
		refsDir = fs.resolve(gitDir, R_REFS);
		logsDir = fs.resolve(gitDir, LOGS);
		logsRefsDir = fs.resolve(gitDir, LOGS + '/' + R_REFS);
		packedRefsFile = fs.resolve(gitDir, PACKED_REFS);

		looseRefs.set(RefList.<LooseRef> emptyList());
		packedRefs.set(PackedRefList.NO_PACKED_REFS);
	}

	Repository getRepository() {
		return parent;
	}

	public void create() throws IOException {
		refsDir.mkdir();
		logsDir.mkdir();
		logsRefsDir.mkdir();

		new File(refsDir, R_HEADS.substring(R_REFS.length())).mkdir();
		new File(refsDir, R_TAGS.substring(R_REFS.length())).mkdir();
		new File(logsRefsDir, R_HEADS.substring(R_REFS.length())).mkdir();
	}

	@Override
	public void close() {
		// We have no resources to close.
	}

	void rescan() {
		looseRefs.set(RefList.<LooseRef> emptyList());
		packedRefs.set(PackedRefList.NO_PACKED_REFS);
	}

	@Override
	public boolean isNameConflicting(String name) throws IOException {
		RefList<Ref> packed = getPackedRefs();
		RefList<LooseRef> loose = getLooseRefs();

		// Cannot be nested within an existing reference.
		int lastSlash = name.lastIndexOf('/');
		while (0 < lastSlash) {
			String needle = name.substring(0, lastSlash);
			if (loose.contains(needle) || packed.contains(needle))
				return true;
			lastSlash = name.lastIndexOf('/', lastSlash - 1);
		}

		// Cannot be the container of an existing reference.
		String prefix = name + '/';
		int idx;

		idx = -(packed.find(prefix) + 1);
		if (idx < packed.size() && packed.get(idx).getName().startsWith(prefix))
			return true;

		idx = -(loose.find(prefix) + 1);
		if (idx < loose.size() && loose.get(idx).getName().startsWith(prefix))
			return true;

		return false;
	}

	private RefList<LooseRef> getLooseRefs() {
		final RefList<LooseRef> oldLoose = looseRefs.get();

		LooseScanner scan = new LooseScanner(oldLoose);
		scan.scan(ALL);

		RefList<LooseRef> loose;
		if (scan.newLoose != null) {
			loose = scan.newLoose.toRefList();
			if (looseRefs.compareAndSet(oldLoose, loose))
				modCnt.incrementAndGet();
		} else
			loose = oldLoose;
		return loose;
	}

	@Override
	public Ref getRef(final String needle) throws IOException {
		final RefList<Ref> packed = getPackedRefs();
		Ref ref = null;
		for (String prefix : SEARCH_PATH) {
			ref = readRef(prefix + needle, packed);
			if (ref != null) {
				ref = resolve(ref, 0, null, null, packed);
				break;
			}
		}
		fireRefsChanged();
		return ref;
	}

	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		final RefList<Ref> packed = getPackedRefs();
		final RefList<LooseRef> oldLoose = looseRefs.get();

		LooseScanner scan = new LooseScanner(oldLoose);
		scan.scan(prefix);

		RefList<LooseRef> loose;
		if (scan.newLoose != null) {
			loose = scan.newLoose.toRefList();
			if (looseRefs.compareAndSet(oldLoose, loose))
				modCnt.incrementAndGet();
		} else
			loose = oldLoose;
		fireRefsChanged();

		RefList.Builder<Ref> symbolic = scan.symbolic;
		for (int idx = 0; idx < symbolic.size();) {
			Ref ref = symbolic.get(idx);
			ref = resolve(ref, 0, prefix, loose, packed);
			if (ref != null && ref.getObjectId() != null) {
				symbolic.set(idx, ref);
				idx++;
			} else {
				// A broken symbolic reference, we have to drop it from the
				// collections the client is about to receive. Should be a
				// rare occurrence so pay a copy penalty.
				loose = loose.remove(idx);
				symbolic.remove(idx);
			}
		}

		return new RefMap(prefix, packed, upcast(loose), symbolic.toRefList());
	}

	@SuppressWarnings("unchecked")
	private RefList<Ref> upcast(RefList<? extends Ref> loose) {
		return (RefList<Ref>) loose;
	}

	private class LooseScanner {
		private final RefList<LooseRef> curLoose;

		private int curIdx;

		final RefList.Builder<Ref> symbolic = new RefList.Builder<Ref>(4);

		RefList.Builder<LooseRef> newLoose;

		LooseScanner(final RefList<LooseRef> curLoose) {
			this.curLoose = curLoose;
		}

		void scan(String prefix) {
			if (ALL.equals(prefix)) {
				scanOne(HEAD);
				scanTree(R_REFS, refsDir);

				// If any entries remain, they are deleted, drop them.
				if (newLoose == null && curIdx < curLoose.size())
					newLoose = curLoose.copy(curIdx);

			} else if (prefix.startsWith(R_REFS) && prefix.endsWith("/")) {
				curIdx = -(curLoose.find(prefix) + 1);
				File dir = new File(refsDir, prefix.substring(R_REFS.length()));
				scanTree(prefix, dir);

				// Skip over entries still within the prefix; these have
				// been removed from the directory.
				while (curIdx < curLoose.size()) {
					if (!curLoose.get(curIdx).getName().startsWith(prefix))
						break;
					if (newLoose == null)
						newLoose = curLoose.copy(curIdx);
					curIdx++;
				}

				// Keep any entries outside of the prefix space, we
				// do not know anything about their status.
				if (newLoose != null) {
					while (curIdx < curLoose.size())
						newLoose.add(curLoose.get(curIdx++));
				}
			}
		}

		private boolean scanTree(String prefix, File dir) {
			final String[] entries = dir.list(LockFile.FILTER);
			if (entries == null) // not a directory or an I/O error
				return false;
			if (0 < entries.length) {
				Arrays.sort(entries);
				for (String name : entries) {
					File e = new File(dir, name);
					if (!scanTree(prefix + name + '/', e))
						scanOne(prefix + name);
				}
			}
			return true;
		}

		private void scanOne(String name) {
			LooseRef cur;

			if (curIdx < curLoose.size()) {
				do {
					cur = curLoose.get(curIdx);
					int cmp = RefComparator.compareTo(cur, name);
					if (cmp < 0) {
						// Reference is not loose anymore, its been deleted.
						// Skip the name in the new result list.
						if (newLoose == null)
							newLoose = curLoose.copy(curIdx);
						curIdx++;
						cur = null;
						continue;
					}

					if (cmp > 0) // Newly discovered loose reference.
						cur = null;
					break;
				} while (curIdx < curLoose.size());
			} else
				cur = null; // Newly discovered loose reference.

			LooseRef n;
			try {
				n = scanRef(cur, name);
			} catch (IOException notValid) {
				n = null;
			}

			if (n != null) {
				if (cur != n && newLoose == null)
					newLoose = curLoose.copy(curIdx);
				if (newLoose != null)
					newLoose.add(n);
				if (n.isSymbolic())
					symbolic.add(n);
			} else if (cur != null) {
				// Tragically, this file is no longer a loose reference.
				// Kill our cached entry of it.
				if (newLoose == null)
					newLoose = curLoose.copy(curIdx);
			}

			if (cur != null)
				curIdx++;
		}
	}

	@Override
	public Ref peel(final Ref ref) throws IOException {
		final Ref leaf = ref.getLeaf();
		if (leaf.isPeeled() || leaf.getObjectId() == null)
			return ref;

		RevWalk rw = new RevWalk(getRepository());
		RevObject obj = rw.parseAny(leaf.getObjectId());
		ObjectIdRef newLeaf;
		if (obj instanceof RevTag) {
			newLeaf = new ObjectIdRef.PeeledTag(leaf.getStorage(), leaf
					.getName(), leaf.getObjectId(), rw.peel(obj).copy());
		} else {
			newLeaf = new ObjectIdRef.PeeledNonTag(leaf.getStorage(), leaf
					.getName(), leaf.getObjectId());
		}

		// Try to remember this peeling in the cache, so we don't have to do
		// it again in the future, but only if the reference is unchanged.
		if (leaf.getStorage().isLoose()) {
			RefList<LooseRef> curList = looseRefs.get();
			int idx = curList.find(leaf.getName());
			if (0 <= idx && curList.get(idx) == leaf) {
				LooseRef asPeeled = ((LooseRef) leaf).peel(newLeaf);
				RefList<LooseRef> newList = curList.set(idx, asPeeled);
				looseRefs.compareAndSet(curList, newList);
			}
		}

		return recreate(ref, newLeaf);
	}

	private static Ref recreate(final Ref old, final ObjectIdRef leaf) {
		if (old.isSymbolic()) {
			Ref dst = recreate(old.getTarget(), leaf);
			return new SymbolicRef(old.getName(), dst);
		}
		return leaf;
	}

	void storedSymbolicRef(RefDirectoryUpdate u, long modified, String target) {
		putLooseRef(newSymbolicRef(modified, u.getRef().getName(), target));
		fireRefsChanged();
	}

	public RefDirectoryUpdate newUpdate(String name, boolean detach)
			throws IOException {
		final RefList<Ref> packed = getPackedRefs();
		Ref ref = readRef(name, packed);
		if (ref != null)
			ref = resolve(ref, 0, null, null, packed);
		if (ref == null)
			ref = new ObjectIdRef.Unpeeled(NEW, name, null);
		else if (detach && ref.isSymbolic())
			ref = new ObjectIdRef.Unpeeled(LOOSE, name, ref.getObjectId());
		return new RefDirectoryUpdate(this, ref);
	}

	@Override
	public RefDirectoryRename newRename(String fromName, String toName)
			throws IOException {
		RefDirectoryUpdate from = newUpdate(fromName, false);
		RefDirectoryUpdate to = newUpdate(toName, false);
		return new RefDirectoryRename(from, to);
	}

	void stored(RefDirectoryUpdate update, long modified) {
		final ObjectId target = update.getNewObjectId().copy();
		final Ref leaf = update.getRef().getLeaf();
		putLooseRef(new LooseUnpeeled(modified, leaf.getName(), target));
	}

	private void putLooseRef(LooseRef ref) {
		RefList<LooseRef> cList, nList;
		do {
			cList = looseRefs.get();
			nList = cList.put(ref);
		} while (!looseRefs.compareAndSet(cList, nList));
		modCnt.incrementAndGet();
		fireRefsChanged();
	}

	void delete(RefDirectoryUpdate update) throws IOException {
		Ref dst = update.getRef().getLeaf();
		String name = dst.getName();

		// Write the packed-refs file using an atomic update. We might
		// wind up reading it twice, before and after the lock, to ensure
		// we don't miss an edit made externally.
		final PackedRefList packed = getPackedRefs();
		if (packed.contains(name)) {
			LockFile lck = new LockFile(packedRefsFile);
			if (!lck.lock())
				throw new IOException(MessageFormat.format(
					JGitText.get().cannotLockFile, packedRefsFile));
			try {
				PackedRefList cur = readPackedRefs(0, 0);
				int idx = cur.find(name);
				if (0 <= idx)
					commitPackedRefs(lck, cur.remove(idx), packed);
			} finally {
				lck.unlock();
			}
		}

		RefList<LooseRef> curLoose, newLoose;
		do {
			curLoose = looseRefs.get();
			int idx = curLoose.find(name);
			if (idx < 0)
				break;
			newLoose = curLoose.remove(idx);
		} while (!looseRefs.compareAndSet(curLoose, newLoose));

		int levels = levelsIn(name) - 2;
		delete(logFor(name), levels);
		if (dst.getStorage().isLoose()) {
			update.unlock();
			delete(fileFor(name), levels);
		}

		modCnt.incrementAndGet();
		fireRefsChanged();
	}

	void log(final RefUpdate update, final String msg, final boolean deref)
			throws IOException {
		final ObjectId oldId = update.getOldObjectId();
		final ObjectId newId = update.getNewObjectId();
		final Ref ref = update.getRef();

		PersonIdent ident = update.getRefLogIdent();
		if (ident == null)
			ident = new PersonIdent(parent);
		else
			ident = new PersonIdent(ident);

		final StringBuilder r = new StringBuilder();
		r.append(ObjectId.toString(oldId));
		r.append(' ');
		r.append(ObjectId.toString(newId));
		r.append(' ');
		r.append(ident.toExternalString());
		r.append('\t');
		r.append(msg);
		r.append('\n');
		final byte[] rec = encode(r.toString());

		if (deref && ref.isSymbolic()) {
			log(ref.getName(), rec);
			log(ref.getLeaf().getName(), rec);
		} else {
			log(ref.getName(), rec);
		}
	}

	private void log(final String refName, final byte[] rec) throws IOException {
		final File log = logFor(refName);
		final boolean write;
		if (isLogAllRefUpdates() && shouldAutoCreateLog(refName))
			write = true;
		else if (log.isFile())
			write = true;
		else
			write = false;

		if (write) {
			FileOutputStream out;
			try {
				out = new FileOutputStream(log, true);
			} catch (FileNotFoundException err) {
				final File dir = log.getParentFile();
				if (dir.exists())
					throw err;
				if (!dir.mkdirs() && !dir.isDirectory())
					throw new IOException(MessageFormat.format(JGitText.get().cannotCreateDirectory, dir));
				out = new FileOutputStream(log, true);
			}
			try {
				out.write(rec);
			} finally {
				out.close();
			}
		}
	}

	private boolean isLogAllRefUpdates() {
		return parent.getConfig().get(CoreConfig.KEY).isLogAllRefUpdates();
	}

	private boolean shouldAutoCreateLog(final String refName) {
		return refName.equals(HEAD) //
				|| refName.startsWith(R_HEADS) //
				|| refName.startsWith(R_REMOTES);
	}

	private Ref resolve(final Ref ref, int depth, String prefix,
			RefList<LooseRef> loose, RefList<Ref> packed) throws IOException {
		if (ref.isSymbolic()) {
			Ref dst = ref.getTarget();

			if (MAX_SYMBOLIC_REF_DEPTH <= depth)
				return null; // claim it doesn't exist

			// If the cached value can be assumed to be current due to a
			// recent scan of the loose directory, use it.
			if (loose != null && dst.getName().startsWith(prefix)) {
				int idx;
				if (0 <= (idx = loose.find(dst.getName())))
					dst = loose.get(idx);
				else if (0 <= (idx = packed.find(dst.getName())))
					dst = packed.get(idx);
				else
					return ref;
			} else {
				dst = readRef(dst.getName(), packed);
				if (dst == null)
					return ref;
			}

			dst = resolve(dst, depth + 1, prefix, loose, packed);
			if (dst == null)
				return null;
			return new SymbolicRef(ref.getName(), dst);
		}
		return ref;
	}

	private PackedRefList getPackedRefs() throws IOException {
		long size = packedRefsFile.length();
		long mtime = size != 0 ? packedRefsFile.lastModified() : 0;

		final PackedRefList curList = packedRefs.get();
		if (size == curList.lastSize && mtime == curList.lastModified)
			return curList;

		final PackedRefList newList = readPackedRefs(size, mtime);
		if (packedRefs.compareAndSet(curList, newList))
			modCnt.incrementAndGet();
		return newList;
	}

	private PackedRefList readPackedRefs(long size, long mtime)
			throws IOException {
		final BufferedReader br;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(
					packedRefsFile), CHARSET));
		} catch (FileNotFoundException noPackedRefs) {
			// Ignore it and leave the new list empty.
			return PackedRefList.NO_PACKED_REFS;
		}
		try {
			return new PackedRefList(parsePackedRefs(br), size, mtime);
		} finally {
			br.close();
		}
	}

	private RefList<Ref> parsePackedRefs(final BufferedReader br)
			throws IOException {
		RefList.Builder<Ref> all = new RefList.Builder<Ref>();
		Ref last = null;
		boolean peeled = false;
		boolean needSort = false;

		String p;
		while ((p = br.readLine()) != null) {
			if (p.charAt(0) == '#') {
				if (p.startsWith(PACKED_REFS_HEADER)) {
					p = p.substring(PACKED_REFS_HEADER.length());
					peeled = p.contains(PACKED_REFS_PEELED);
				}
				continue;
			}

			if (p.charAt(0) == '^') {
				if (last == null)
					throw new IOException(JGitText.get().peeledLineBeforeRef);

				ObjectId id = ObjectId.fromString(p.substring(1));
				last = new ObjectIdRef.PeeledTag(PACKED, last.getName(), last
						.getObjectId(), id);
				all.set(all.size() - 1, last);
				continue;
			}

			int sp = p.indexOf(' ');
			ObjectId id = ObjectId.fromString(p.substring(0, sp));
			String name = copy(p, sp + 1, p.length());
			ObjectIdRef cur;
			if (peeled)
				cur = new ObjectIdRef.PeeledNonTag(PACKED, name, id);
			else
				cur = new ObjectIdRef.Unpeeled(PACKED, name, id);
			if (last != null && RefComparator.compareTo(last, cur) > 0)
				needSort = true;
			all.add(cur);
			last = cur;
		}

		if (needSort)
			all.sort();
		return all.toRefList();
	}

	private static String copy(final String src, final int off, final int end) {
		// Don't use substring since it could leave a reference to the much
		// larger existing string. Force construction of a full new object.
		return new StringBuilder(end - off).append(src, off, end).toString();
	}

	private void commitPackedRefs(final LockFile lck, final RefList<Ref> refs,
			final PackedRefList oldPackedList) throws IOException {
		new RefWriter(refs) {
			@Override
			protected void writeFile(String name, byte[] content)
					throws IOException {
				lck.setNeedStatInformation(true);
				try {
					lck.write(content);
				} catch (IOException ioe) {
					throw new ObjectWritingException(MessageFormat.format(JGitText.get().unableToWrite, name), ioe);
				}
				try {
					lck.waitForStatChange();
				} catch (InterruptedException e) {
					lck.unlock();
					throw new ObjectWritingException(MessageFormat.format(JGitText.get().interruptedWriting, name));
				}
				if (!lck.commit())
					throw new ObjectWritingException(MessageFormat.format(JGitText.get().unableToWrite, name));

				packedRefs.compareAndSet(oldPackedList, new PackedRefList(refs,
						content.length, lck.getCommitLastModified()));
			}
		}.writePackedRefs();
	}

	private Ref readRef(String name, RefList<Ref> packed) throws IOException {
		final RefList<LooseRef> curList = looseRefs.get();
		final int idx = curList.find(name);
		if (0 <= idx) {
			final LooseRef o = curList.get(idx);
			final LooseRef n = scanRef(o, name);
			if (n == null) {
				if (looseRefs.compareAndSet(curList, curList.remove(idx)))
					modCnt.incrementAndGet();
				return packed.get(name);
			}

			if (o == n)
				return n;
			if (looseRefs.compareAndSet(curList, curList.set(idx, n)))
				modCnt.incrementAndGet();
			return n;
		}

		final LooseRef n = scanRef(null, name);
		if (n == null)
			return packed.get(name);
		if (looseRefs.compareAndSet(curList, curList.add(idx, n)))
			modCnt.incrementAndGet();
		return n;
	}

	private LooseRef scanRef(LooseRef ref, String name) throws IOException {
		final File path = fileFor(name);
		final long modified = path.lastModified();

		if (ref != null) {
			if (ref.getLastModified() == modified)
				return ref;
			name = ref.getName();
		} else if (modified == 0)
			return null;

		final byte[] buf;
		try {
			buf = IO.readFully(path, 4096);
		} catch (FileNotFoundException noFile) {
			return null; // doesn't exist; not a reference.
		}

		int n = buf.length;
		if (n == 0)
			return null; // empty file; not a reference.

		if (isSymRef(buf, n)) {
			// trim trailing whitespace
			while (0 < n && Character.isWhitespace(buf[n - 1]))
				n--;
			if (n < 6) {
				String content = RawParseUtils.decode(buf, 0, n);
				throw new IOException(MessageFormat.format(JGitText.get().notARef, name, content));
			}
			final String target = RawParseUtils.decode(buf, 5, n);
			return newSymbolicRef(modified, name, target);
		}

		if (n < OBJECT_ID_STRING_LENGTH)
			return null; // impossibly short object identifier; not a reference.

		final ObjectId id;
		try {
			id = ObjectId.fromString(buf, 0);
		} catch (IllegalArgumentException notRef) {
			while (0 < n && Character.isWhitespace(buf[n - 1]))
				n--;
			String content = RawParseUtils.decode(buf, 0, n);
			throw new IOException(MessageFormat.format(JGitText.get().notARef, name, content));
		}
		return new LooseUnpeeled(modified, name, id);
	}

	private static boolean isSymRef(final byte[] buf, int n) {
		if (n < 6)
			return false;
		return /**/buf[0] == 'r' //
				&& buf[1] == 'e' //
				&& buf[2] == 'f' //
				&& buf[3] == ':' //
				&& buf[4] == ' ';
	}

	/** If the parent should fire listeners, fires them. */
	private void fireRefsChanged() {
		final int last = lastNotifiedModCnt.get();
		final int curr = modCnt.get();
		if (last != curr && lastNotifiedModCnt.compareAndSet(last, curr))
			parent.fireEvent(new RefsChangedEvent());
	}

	/**
	 * Create a reference update to write a temporary reference.
	 *
	 * @return an update for a new temporary reference.
	 * @throws IOException
	 *             a temporary name cannot be allocated.
	 */
	RefDirectoryUpdate newTemporaryUpdate() throws IOException {
		File tmp = File.createTempFile("renamed_", "_ref", refsDir);
		String name = Constants.R_REFS + tmp.getName();
		Ref ref = new ObjectIdRef.Unpeeled(NEW, name, null);
		return new RefDirectoryUpdate(this, ref);
	}

	/**
	 * Locate the file on disk for a single reference name.
	 *
	 * @param name
	 *            name of the ref, relative to the Git repository top level
	 *            directory (so typically starts with refs/).
	 * @return the loose file location.
	 */
	File fileFor(String name) {
		if (name.startsWith(R_REFS)) {
			name = name.substring(R_REFS.length());
			return new File(refsDir, name);
		}
		return new File(gitDir, name);
	}

	/**
	 * Locate the log file on disk for a single reference name.
	 *
	 * @param name
	 *            name of the ref, relative to the Git repository top level
	 *            directory (so typically starts with refs/).
	 * @return the log file location.
	 */
	File logFor(String name) {
		if (name.startsWith(R_REFS)) {
			name = name.substring(R_REFS.length());
			return new File(logsRefsDir, name);
		}
		return new File(logsDir, name);
	}

	static int levelsIn(final String name) {
		int count = 0;
		for (int p = name.indexOf('/'); p >= 0; p = name.indexOf('/', p + 1))
			count++;
		return count;
	}

	static void delete(final File file, final int depth) throws IOException {
		if (!file.delete() && file.isFile())
			throw new IOException(MessageFormat.format(JGitText.get().fileCannotBeDeleted, file));

		File dir = file.getParentFile();
		for (int i = 0; i < depth; ++i) {
			if (!dir.delete())
				break; // ignore problem here
			dir = dir.getParentFile();
		}
	}

	private static class PackedRefList extends RefList<Ref> {
		static final PackedRefList NO_PACKED_REFS = new PackedRefList(RefList
				.emptyList(), 0, 0);

		/** Last length of the packed-refs file when we read it. */
		final long lastSize;

		/** Last modified time of the packed-refs file when we read it. */
		final long lastModified;

		PackedRefList(RefList<Ref> src, long size, long mtime) {
			super(src);
			lastSize = size;
			lastModified = mtime;
		}
	}

	private static LooseSymbolicRef newSymbolicRef(long lastModified,
			String name, String target) {
		Ref dst = new ObjectIdRef.Unpeeled(NEW, target, null);
		return new LooseSymbolicRef(lastModified, name, dst);
	}

	private static interface LooseRef extends Ref {
		long getLastModified();

		LooseRef peel(ObjectIdRef newLeaf);
	}

	private final static class LoosePeeledTag extends ObjectIdRef.PeeledTag
			implements LooseRef {
		private final long lastModified;

		LoosePeeledTag(long mtime, String refName, ObjectId id, ObjectId p) {
			super(LOOSE, refName, id, p);
			this.lastModified = mtime;
		}

		public long getLastModified() {
			return lastModified;
		}

		public LooseRef peel(ObjectIdRef newLeaf) {
			return this;
		}
	}

	private final static class LooseNonTag extends ObjectIdRef.PeeledNonTag
			implements LooseRef {
		private final long lastModified;

		LooseNonTag(long mtime, String refName, ObjectId id) {
			super(LOOSE, refName, id);
			this.lastModified = mtime;
		}

		public long getLastModified() {
			return lastModified;
		}

		public LooseRef peel(ObjectIdRef newLeaf) {
			return this;
		}
	}

	private final static class LooseUnpeeled extends ObjectIdRef.Unpeeled
			implements LooseRef {
		private final long lastModified;

		LooseUnpeeled(long mtime, String refName, ObjectId id) {
			super(LOOSE, refName, id);
			this.lastModified = mtime;
		}

		public long getLastModified() {
			return lastModified;
		}

		public LooseRef peel(ObjectIdRef newLeaf) {
			if (newLeaf.getPeeledObjectId() != null)
				return new LoosePeeledTag(lastModified, getName(),
						getObjectId(), newLeaf.getPeeledObjectId());
			else
				return new LooseNonTag(lastModified, getName(), getObjectId());
		}
	}

	private final static class LooseSymbolicRef extends SymbolicRef implements
			LooseRef {
		private final long lastModified;

		LooseSymbolicRef(long mtime, String refName, Ref target) {
			super(refName, target);
			this.lastModified = mtime;
		}

		public long getLastModified() {
			return lastModified;
		}

		public LooseRef peel(ObjectIdRef newLeaf) {
			// We should never try to peel the symbolic references.
			throw new UnsupportedOperationException();
		}
	}
}
