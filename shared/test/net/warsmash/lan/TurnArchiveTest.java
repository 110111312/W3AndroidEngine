package net.warsmash.lan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;
import java.security.SecureRandom;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanProtocolException;
import net.warsmash.lan.protocol.SampleMessages;

class TurnArchiveTest {
	private static File temp() throws Exception {
		final File f = File.createTempFile("lan-turns", ".bin");
		f.deleteOnExit();
		return f;
	}

	@Test
	void keepsTurnsInOrderInMemory() throws Exception {
		final TurnArchive a = new TurnArchive();
		for (int i = 0; i < 50; i++) {
			a.append(SampleMessages.turn(i));
		}
		assertEquals(50, a.size());
		long steps = 0;
		for (int i = 0; i < 50; i++) {
			steps += SampleMessages.turn(i).steps;
		}
		assertEquals(steps, a.totalSteps());
		assertEquals(7, a.get(7).n);
		final List<TurnData> r = a.range(10, 15);
		assertEquals(5, r.size());
		assertEquals(10, r.get(0).n);
		assertEquals(50, a.range(-5, 1000).size());
		assertThrows(LanProtocolException.class, () -> a.append(SampleMessages.turn(51)));
		assertThrows(LanProtocolException.class, () -> a.get(50));
		a.close();
	}

	@Test
	void diskCopySurvivesAndTruncatedTailIsCut() throws Exception {
		final File f = temp();
		final TurnArchive a = TurnArchive.create(f);
		for (int i = 0; i < 30; i++) {
			a.append(SampleMessages.turn(i));
		}
		a.close();
		final long full = f.length();
		assertTrue(full > 0);

		final TurnArchive b = TurnArchive.recover(f);
		assertEquals(30, b.size());
		long recovered = 0;
		for (int i = 0; i < 30; i++) {
			recovered += SampleMessages.turn(i).steps;
		}
		assertEquals(recovered, b.totalSteps());
		assertArrayEquals(SampleMessages.turn(12).toBytes(), b.get(12).toBytes());
		b.append(SampleMessages.turn(30));
		b.close();

		// a crash in the middle of the last record
		try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
			raf.setLength(raf.length() - 5);
		}
		final TurnArchive c = TurnArchive.recover(f);
		assertEquals(30, c.size());
		c.append(SampleMessages.turn(30));
		c.close();
		final TurnArchive d = TurnArchive.recover(f);
		assertEquals(31, d.size());
		d.close();

		// a damaged length in the middle stops recovery there instead of failing
		try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
			raf.seek(0);
			raf.writeInt(0x7FFFFFFF);
		}
		final TurnArchive e = TurnArchive.recover(f);
		assertEquals(0, e.size());
		e.close();
		f.delete();
	}

	@Test
	void rejoinKeyRoundTripsThroughTextAndDisk() throws Exception {
		final RejoinKey key = RejoinKey.create(0xFEDCBA9876543210L, new SecureRandom());
		final RejoinKey parsed = RejoinKey.parse(key.serialize());
		assertNotNull(parsed);
		assertEquals(key.gameId, parsed.gameId);
		assertTrue(key.matches(parsed.gameId, parsed.token()));
		assertTrue(!key.matches(parsed.gameId + 1, parsed.token()));
		final byte[] other = parsed.token();
		other[3] ^= 1;
		assertTrue(!key.matches(key.gameId, other));

		final File f = temp();
		key.save(f);
		final RejoinKey loaded = RejoinKey.load(f);
		assertNotNull(loaded);
		assertEquals(key.serialize(), loaded.serialize());
		RejoinKey.delete(f);
		assertNull(RejoinKey.load(f));
	}

	@Test
	void damagedRejoinKeysAreRefused() {
		assertNull(RejoinKey.parse(""));
		assertNull(RejoinKey.parse("zz:00"));
		assertNull(RejoinKey.parse("1:" + "0".repeat(31)));
		assertNull(RejoinKey.parse("1:" + "g".repeat(32)));
		assertNull(RejoinKey.parse(":" + "0".repeat(32)));
	}
}
