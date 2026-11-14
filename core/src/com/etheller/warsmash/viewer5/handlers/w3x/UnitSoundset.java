package com.etheller.warsmash.viewer5.handlers.w3x;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.DataTable;

public class UnitSoundset {
	public final UnitSound what;
	public final UnitSound pissed;
	public final UnitSound yesAttack;
	public final UnitSound yes;
	public final UnitSound ready;
	public final UnitSound warcry;

	public UnitSoundset(final DataSource dataSource, final DataTable unitAckSounds, final String soundName) {
		this.what = UnitSound.create(dataSource, unitAckSounds, soundName, "What");
		this.pissed = UnitSound.create(dataSource, unitAckSounds, soundName, "Pissed");
		this.yesAttack = UnitSound.create(dataSource, unitAckSounds, soundName, "YesAttack");
		this.yes = UnitSound.create(dataSource, unitAckSounds, soundName, "Yes");
		this.ready = UnitSound.create(dataSource, unitAckSounds, soundName, "Ready");
		this.warcry = UnitSound.create(dataSource, unitAckSounds, soundName, "Warcry");
	}

	/** Decodes the answers a player hears when he selects and orders a unit of his own. */
	public void preload() {
		this.what.preload();
		this.yes.preload();
		this.yesAttack.preload();
		this.pissed.preload();
	}

	/** Starts the decoding preload asks for, off this thread where a backend can. */
	public void prefetch() {
		this.what.prefetch();
		this.yes.prefetch();
		this.yesAttack.prefetch();
		this.pissed.prefetch();
	}

}
