package net.warsmash.lan.protocol;

public abstract class LanMessage {
	public abstract int type();

	public abstract void write(ByteWriter w) throws LanProtocolException;

	/** The size of the written body when it is known beforehand (a big message), else 0: its buffer is made at once. */
	public int sizeHint() {
		return 0;
	}
}
