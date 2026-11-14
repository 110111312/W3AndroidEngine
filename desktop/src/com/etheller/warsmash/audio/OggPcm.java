package com.etheller.warsmash.audio;

/** A decoded ogg sound, ready to be given to OpenAL. */
final class OggPcm {
	final byte[] pcm;
	final int channels;
	final int sampleRate;

	OggPcm(final byte[] pcm, final int channels, final int sampleRate) {
		this.pcm = pcm;
		this.channels = channels;
		this.sampleRate = sampleRate;
	}
}
