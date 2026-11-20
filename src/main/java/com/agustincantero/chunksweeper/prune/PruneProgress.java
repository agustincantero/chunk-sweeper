package com.agustincantero.chunksweeper.prune;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Progress of a running prune, written by the worker thread and read by the render thread.
 */
public final class PruneProgress {
	private final AtomicInteger done = new AtomicInteger();
	private final AtomicInteger removedChunks = new AtomicInteger();
	private final AtomicLong bytesFreed = new AtomicLong();
	private volatile int total;

	void start(int total) {
		this.total = total;
		this.done.set(0);
		this.removedChunks.set(0);
		this.bytesFreed.set(0);
	}

	void step() {
		this.done.incrementAndGet();
	}

	void addRemovedChunks(int chunks) {
		this.removedChunks.addAndGet(chunks);
	}

	void addBytesFreed(long bytes) {
		this.bytesFreed.addAndGet(bytes);
	}

	public int done() {
		return this.done.get();
	}

	public int total() {
		return this.total;
	}

	/**
	 * Fraction of the work that is done, from 0 to 1.
	 */
	public float fraction() {
		int total = this.total;
		return total <= 0 ? 0 : Math.min(1, (float) this.done.get() / total);
	}

	public int removedChunks() {
		return this.removedChunks.get();
	}

	/**
	 * Bytes freed so far. Only updated by an in-place prune.
	 */
	public long bytesFreed() {
		return this.bytesFreed.get();
	}
}
