/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevCommitList;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.PackLock;
import org.eclipse.jgit.transport.PacketLineIn.AckNackResult;
import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * Fetch implementation using the native Git pack transfer service.
 * <p>
 * This is the canonical implementation for transferring objects from the remote
 * repository to the local repository by talking to the 'git-upload-pack'
 * service. Objects are packed on the remote side into a pack file and then sent
 * down the pipe to us.
 * <p>
 * This connection requires only a bi-directional pipe or socket, and thus is
 * easily wrapped up into a local process pipe, anonymous TCP socket, or a
 * command executed through an SSH tunnel.
 * <p>
 * If {@link BasePackConnection#statelessRPC} is {@code true}, this connection
 * can be tunneled over a request-response style RPC system like HTTP.  The RPC
 * call boundary is determined by this class switching from writing to the
 * OutputStream to reading from the InputStream.
 * <p>
 * Concrete implementations should just call
 * {@link #init(java.io.InputStream, java.io.OutputStream)} and
 * {@link #readAdvertisedRefs()} methods in constructor or before any use. They
 * should also handle resources releasing in {@link #close()} method if needed.
 */
abstract class BasePackFetchConnection extends BasePackConnection implements
		FetchConnection {
	/**
	 * Maximum number of 'have' lines to send before giving up.
	 * <p>
	 * During {@link #negotiate(ProgressMonitor)} we send at most this many
	 * commits to the remote peer as 'have' lines without an ACK response before
	 * we give up.
	 */
	private static final int MAX_HAVES = 256;

	/**
	 * Amount of data the client sends before starting to read.
	 * <p>
	 * Any output stream given to the client must be able to buffer this many
	 * bytes before the client will stop writing and start reading from the
	 * input stream. If the output stream blocks before this many bytes are in
	 * the send queue, the system will deadlock.
	 */
	protected static final int MIN_CLIENT_BUFFER = 2 * 32 * 46 + 8;

	static final String OPTION_INCLUDE_TAG = "include-tag";

	static final String OPTION_MULTI_ACK = "multi_ack";

	static final String OPTION_MULTI_ACK_DETAILED = "multi_ack_detailed";

	static final String OPTION_THIN_PACK = "thin-pack";

	static final String OPTION_SIDE_BAND = "side-band";

	static final String OPTION_SIDE_BAND_64K = "side-band-64k";

	static final String OPTION_OFS_DELTA = "ofs-delta";

	static final String OPTION_SHALLOW = "shallow";

	static final String OPTION_NO_PROGRESS = "no-progress";

	static enum MultiAck {
		OFF, CONTINUE, DETAILED;
	}

	private final RevWalk walk;

	/** All commits that are immediately reachable by a local ref. */
	private RevCommitList<RevCommit> reachableCommits;

	/** Marks an object as having all its dependencies. */
	final RevFlag REACHABLE;

	/** Marks a commit known to both sides of the connection. */
	final RevFlag COMMON;

	/** Like {@link #COMMON} but means its also in {@link #pckState}. */
	private final RevFlag STATE;

	/** Marks a commit listed in the advertised refs. */
	final RevFlag ADVERTISED;

	private MultiAck multiAck = MultiAck.OFF;

	private boolean thinPack;

	private boolean sideband;

	private boolean includeTags;

	private boolean allowOfsDelta;

	private String lockMessage;

	private PackLock packLock;

	/** RPC state, if {@link BasePackConnection#statelessRPC} is true. */
	private TemporaryBuffer.Heap state;

	private PacketLineOut pckState;

	BasePackFetchConnection(final PackTransport packTransport) {
		super(packTransport);

		final FetchConfig cfg = local.getConfig().get(FetchConfig.KEY);
		includeTags = transport.getTagOpt() != TagOpt.NO_TAGS;
		thinPack = transport.isFetchThin();
		allowOfsDelta = cfg.allowOfsDelta;

		walk = new RevWalk(local);
		reachableCommits = new RevCommitList<RevCommit>();
		REACHABLE = walk.newFlag("REACHABLE");
		COMMON = walk.newFlag("COMMON");
		STATE = walk.newFlag("STATE");
		ADVERTISED = walk.newFlag("ADVERTISED");

		walk.carry(COMMON);
		walk.carry(REACHABLE);
		walk.carry(ADVERTISED);
	}

	private static class FetchConfig {
		static final SectionParser<FetchConfig> KEY = new SectionParser<FetchConfig>() {
			public FetchConfig parse(final Config cfg) {
				return new FetchConfig(cfg);
			}
		};

		final boolean allowOfsDelta;

		FetchConfig(final Config c) {
			allowOfsDelta = c.getBoolean("repack", "usedeltabaseoffset", true);
		}
	}

	public final void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException {
		markStartedOperation();
		doFetch(monitor, want, have);
	}

	public boolean didFetchIncludeTags() {
		return false;
	}

	public boolean didFetchTestConnectivity() {
		return false;
	}

	public void setPackLockMessage(final String message) {
		lockMessage = message;
	}

	public Collection<PackLock> getPackLocks() {
		if (packLock != null)
			return Collections.singleton(packLock);
		return Collections.<PackLock> emptyList();
	}

	protected void doFetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException {
		try {
			markRefsAdvertised();
			markReachable(have, maxTimeWanted(want));

			if (statelessRPC) {
				state = new TemporaryBuffer.Heap(Integer.MAX_VALUE);
				pckState = new PacketLineOut(state);
			}

			if (sendWants(want)) {
				negotiate(monitor);

				walk.dispose();
				reachableCommits = null;
				state = null;
				pckState = null;

				receivePack(monitor);
			}
		} catch (CancelledException ce) {
			close();
			return; // Caller should test (or just know) this themselves.
		} catch (IOException err) {
			close();
			throw new TransportException(err.getMessage(), err);
		} catch (RuntimeException err) {
			close();
			throw new TransportException(err.getMessage(), err);
		}
	}

	private int maxTimeWanted(final Collection<Ref> wants) {
		int maxTime = 0;
		for (final Ref r : wants) {
			try {
				final RevObject obj = walk.parseAny(r.getObjectId());
				if (obj instanceof RevCommit) {
					final int cTime = ((RevCommit) obj).getCommitTime();
					if (maxTime < cTime)
						maxTime = cTime;
				}
			} catch (IOException error) {
				// We don't have it, but we want to fetch (thus fixing error).
			}
		}
		return maxTime;
	}

	private void markReachable(final Set<ObjectId> have, final int maxTime)
			throws IOException {
		for (final Ref r : local.getAllRefs().values()) {
			try {
				final RevCommit o = walk.parseCommit(r.getObjectId());
				o.add(REACHABLE);
				reachableCommits.add(o);
			} catch (IOException readError) {
				// If we cannot read the value of the ref skip it.
			}
		}

		for (final ObjectId id : have) {
			try {
				final RevCommit o = walk.parseCommit(id);
				o.add(REACHABLE);
				reachableCommits.add(o);
			} catch (IOException readError) {
				// If we cannot read the value of the ref skip it.
			}
		}

		if (maxTime > 0) {
			// Mark reachable commits until we reach maxTime. These may
			// wind up later matching up against things we want and we
			// can avoid asking for something we already happen to have.
			//
			final Date maxWhen = new Date(maxTime * 1000L);
			walk.sort(RevSort.COMMIT_TIME_DESC);
			walk.markStart(reachableCommits);
			walk.setRevFilter(CommitTimeRevFilter.after(maxWhen));
			for (;;) {
				final RevCommit c = walk.next();
				if (c == null)
					break;
				if (c.has(ADVERTISED) && !c.has(COMMON)) {
					// This is actually going to be a common commit, but
					// our peer doesn't know that fact yet.
					//
					c.add(COMMON);
					c.carry(COMMON);
					reachableCommits.add(c);
				}
			}
		}
	}

	private boolean sendWants(final Collection<Ref> want) throws IOException {
		final PacketLineOut p = statelessRPC ? pckState : pckOut;
		boolean first = true;
		for (final Ref r : want) {
			try {
				if (walk.parseAny(r.getObjectId()).has(REACHABLE)) {
					// We already have this object. Asking for it is
					// not a very good idea.
					//
					continue;
				}
			} catch (IOException err) {
				// Its OK, we don't have it, but we want to fix that
				// by fetching the object from the other side.
			}

			final StringBuilder line = new StringBuilder(46);
			line.append("want ");
			line.append(r.getObjectId().name());
			if (first) {
				line.append(enableCapabilities());
				first = false;
			}
			line.append('\n');
			p.writeString(line.toString());
		}
		if (first)
			return false;
		p.end();
		outNeedsEnd = false;
		return true;
	}

	private String enableCapabilities() throws TransportException {
		final StringBuilder line = new StringBuilder();
		if (includeTags)
			includeTags = wantCapability(line, OPTION_INCLUDE_TAG);
		if (allowOfsDelta)
			wantCapability(line, OPTION_OFS_DELTA);

		if (wantCapability(line, OPTION_MULTI_ACK_DETAILED))
			multiAck = MultiAck.DETAILED;
		else if (wantCapability(line, OPTION_MULTI_ACK))
			multiAck = MultiAck.CONTINUE;
		else
			multiAck = MultiAck.OFF;

		if (thinPack)
			thinPack = wantCapability(line, OPTION_THIN_PACK);
		if (wantCapability(line, OPTION_SIDE_BAND_64K))
			sideband = true;
		else if (wantCapability(line, OPTION_SIDE_BAND))
			sideband = true;

		if (statelessRPC && multiAck != MultiAck.DETAILED) {
			// Our stateless RPC implementation relies upon the detailed
			// ACK status to tell us common objects for reuse in future
			// requests.  If its not enabled, we can't talk to the peer.
			//
			throw new PackProtocolException(uri, MessageFormat.format(JGitText.get().statelessRPCRequiresOptionToBeEnabled, OPTION_MULTI_ACK_DETAILED));
		}

		return line.toString();
	}

	private void negotiate(final ProgressMonitor monitor) throws IOException,
			CancelledException {
		final MutableObjectId ackId = new MutableObjectId();
		int resultsPending = 0;
		int havesSent = 0;
		int havesSinceLastContinue = 0;
		boolean receivedContinue = false;
		boolean receivedAck = false;

		if (statelessRPC)
			state.writeTo(out, null);

		negotiateBegin();
		SEND_HAVES: for (;;) {
			final RevCommit c = walk.next();
			if (c == null)
				break SEND_HAVES;

			pckOut.writeString("have " + c.getId().name() + "\n");
			havesSent++;
			havesSinceLastContinue++;

			if ((31 & havesSent) != 0) {
				// We group the have lines into blocks of 32, each marked
				// with a flush (aka end). This one is within a block so
				// continue with another have line.
				//
				continue;
			}

			if (monitor.isCancelled())
				throw new CancelledException();

			pckOut.end();
			resultsPending++; // Each end will cause a result to come back.

			if (havesSent == 32 && !statelessRPC) {
				// On the first block we race ahead and try to send
				// more of the second block while waiting for the
				// remote to respond to our first block request.
				// This keeps us one block ahead of the peer.
				//
				continue;
			}

			READ_RESULT: for (;;) {
				final AckNackResult anr = pckIn.readACK(ackId);
				switch (anr) {
				case NAK:
					// More have lines are necessary to compute the
					// pack on the remote side. Keep doing that.
					//
					resultsPending--;
					break READ_RESULT;

				case ACK:
					// The remote side is happy and knows exactly what
					// to send us. There is no further negotiation and
					// we can break out immediately.
					//
					multiAck = MultiAck.OFF;
					resultsPending = 0;
					receivedAck = true;
					if (statelessRPC)
						state.writeTo(out, null);
					break SEND_HAVES;

				case ACK_CONTINUE:
				case ACK_COMMON:
				case ACK_READY:
					// The server knows this commit (ackId). We don't
					// need to send any further along its ancestry, but
					// we need to continue to talk about other parts of
					// our local history.
					//
					markCommon(walk.parseAny(ackId), anr);
					receivedAck = true;
					receivedContinue = true;
					havesSinceLastContinue = 0;
					break;
				}

				if (monitor.isCancelled())
					throw new CancelledException();
			}

			if (statelessRPC)
				state.writeTo(out, null);

			if (receivedContinue && havesSinceLastContinue > MAX_HAVES) {
				// Our history must be really different from the remote's.
				// We just sent a whole slew of have lines, and it did not
				// recognize any of them. Avoid sending our entire history
				// to them by giving up early.
				//
				break SEND_HAVES;
			}
		}

		// Tell the remote side we have run out of things to talk about.
		//
		if (monitor.isCancelled())
			throw new CancelledException();

		// When statelessRPC is true we should always leave SEND_HAVES
		// loop above while in the middle of a request. This allows us
		// to just write done immediately.
		//
		pckOut.writeString("done\n");
		pckOut.flush();

		if (!receivedAck) {
			// Apparently if we have never received an ACK earlier
			// there is one more result expected from the done we
			// just sent to the remote.
			//
			multiAck = MultiAck.OFF;
			resultsPending++;
		}

		READ_RESULT: while (resultsPending > 0 || multiAck != MultiAck.OFF) {
			final AckNackResult anr = pckIn.readACK(ackId);
			resultsPending--;
			switch (anr) {
			case NAK:
				// A NAK is a response to an end we queued earlier
				// we eat it and look for another ACK/NAK message.
				//
				break;

			case ACK:
				// A solitary ACK at this point means the remote won't
				// speak anymore, but is going to send us a pack now.
				//
				break READ_RESULT;

			case ACK_CONTINUE:
			case ACK_COMMON:
			case ACK_READY:
				// We will expect a normal ACK to break out of the loop.
				//
				multiAck = MultiAck.CONTINUE;
				break;
			}

			if (monitor.isCancelled())
				throw new CancelledException();
		}
	}

	private void negotiateBegin() throws IOException {
		walk.resetRetain(REACHABLE, ADVERTISED);
		walk.markStart(reachableCommits);
		walk.sort(RevSort.COMMIT_TIME_DESC);
		walk.setRevFilter(new RevFilter() {
			@Override
			public RevFilter clone() {
				return this;
			}

			@Override
			public boolean include(final RevWalk walker, final RevCommit c) {
				final boolean remoteKnowsIsCommon = c.has(COMMON);
				if (c.has(ADVERTISED)) {
					// Remote advertised this, and we have it, hence common.
					// Whether or not the remote knows that fact is tested
					// before we added the flag. If the remote doesn't know
					// we have to still send them this object.
					//
					c.add(COMMON);
				}
				return !remoteKnowsIsCommon;
			}
		});
	}

	private void markRefsAdvertised() {
		for (final Ref r : getRefs()) {
			markAdvertised(r.getObjectId());
			if (r.getPeeledObjectId() != null)
				markAdvertised(r.getPeeledObjectId());
		}
	}

	private void markAdvertised(final AnyObjectId id) {
		try {
			walk.parseAny(id).add(ADVERTISED);
		} catch (IOException readError) {
			// We probably just do not have this object locally.
		}
	}

	private void markCommon(final RevObject obj, final AckNackResult anr)
			throws IOException {
		if (statelessRPC && anr == AckNackResult.ACK_COMMON && !obj.has(STATE)) {
			StringBuilder s;

			s = new StringBuilder(6 + Constants.OBJECT_ID_STRING_LENGTH);
			s.append("have "); //$NON-NLS-1$
			s.append(obj.name());
			s.append('\n');
			pckState.writeString(s.toString());
			obj.add(STATE);
		}
		obj.add(COMMON);
		if (obj instanceof RevCommit)
			((RevCommit) obj).carry(COMMON);
	}

	private void receivePack(final ProgressMonitor monitor) throws IOException {
		final IndexPack ip;

		InputStream input = in;
		if (sideband)
			input = new SideBandInputStream(input, monitor, getMessageWriter());

		ip = IndexPack.create(local, input);
		ip.setFixThin(thinPack);
		ip.setObjectChecking(transport.isCheckFetchedObjects());
		ip.index(monitor);
		packLock = ip.renameAndOpenPack(lockMessage);
	}

	private static class CancelledException extends Exception {
		private static final long serialVersionUID = 1L;
	}
}
