package com.agustincantero.chunksweeper.prune;

/**
 * @param removedChunks chunks that were left out of the result
 * @param keptChunks chunks that were kept
 * @param bytesBefore total size of the processed region, entities and poi files before pruning
 * @param bytesAfter total size of the same files after pruning
 */
public record PruneResult(int removedChunks, int keptChunks, long bytesBefore, long bytesAfter) {
	public long bytesFreed() {
		return Math.max(0, this.bytesBefore - this.bytesAfter);
	}
}
